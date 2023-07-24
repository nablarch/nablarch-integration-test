package nablarch.fw.web;

import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.MultipartContent;
import jakarta.xml.bind.DatatypeConverter;
import nablarch.common.encryption.AesEncryptor;
import nablarch.common.web.session.EncodeException;
import nablarch.common.web.session.SessionEntry;
import nablarch.common.web.session.encoder.JavaSerializeEncryptStateEncoder;
import nablarch.core.util.FileUtil;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * ウェブアプリケーションにおけるNablarch5以降の標準ハンドラ構成で、
 * Nablarchが正しく動作することを確認する結合テストクラス。
 * <p/>
 * 旧ハンドラ構成と比べて異なる結果となるケースをこのクラスに追加していく。
 */
@RunWith(Arquillian.class)
public class NewWebHandlerQueueIntegrationTest extends WebHandlerQueueIntegrationTestSupport {

    private AesEncryptor aesEncryptor;

    @Before
    public void before() {
        //session-store.xmlに定義したものと同一のキーを指定。
        aesEncryptor = new AesEncryptor();
        aesEncryptor.setKey("1234567890123456");
        aesEncryptor.setIv("9876543210987654");
    }

    @Deployment
    public static WebArchive createDeployment() {
        final WebArchive archive = ShrinkWrap.create(WebArchive.class)
                .addPackage("nablarch.fw.web.app")
                .addPackage("nablarch.fw.web.upload")
                .setWebXML(new File("src/test/webapp/WEB-INF/new-handler-queue-web.xml"));
        
        addTestResources(archive);

        return archive;
    }
    
    /**
     * マルチパートリクエスト時、Hiddenセッションに格納した情報が正しく復元できることを確認するケース。
     * <p/>
     * Hiddenセッションには既に以下の情報が格納されていることを前提とする。
     * <ul>
     * <li>名前："key"</li>
     * <li>値："value"</li>
     * </ul>
     *
     * @throws Exception
     */
    @Test
    @RunAsClient
    public void testMultipart_session() throws Exception {
        // NABLARCH_SIDやJSESSIONIDを生成するため、リクエストを送信
        HttpRequest request = httpTransport.createRequestFactory()
                .buildGetRequest(new GenericUrl(new URL(baseUrl, "action/MultipartAction/PutSession")));
        HttpResponse response = request.execute();

        // アップロードするファイルを作る
        File file = folder.newFile("multipart.txt");

        // hiddenStoreのパラメータを設定
        // (hiddenStore#save相当の処理行い暗号化した後に値を設定する)
        String nablarchSid = "";
        List<String> headerStringValues = response.getHeaders().getHeaderStringValues("set-cookie");
        for (String rawCookie : headerStringValues) {
            if (rawCookie.startsWith("NABLARCH_SID=")) {
                // GlassFish のときは Path だったが Wildfly では path になったので
                // 正規表現で path, Path のどちらでもマッチするようにしていずれのサーバーでも動くようにしている
                nablarchSid = rawCookie.replaceAll("NABLARCH_SID=", "")
                        .replaceAll("; [pP]ath=/; HttpOnly", "");
            }
        }
        List<SessionEntry> entries = new ArrayList<SessionEntry>();
        SessionEntry sessionEntry = new SessionEntry("key", "value", null);
        entries.add(sessionEntry);
        byte[] encrypted = aesEncryptor.encrypt(aesEncryptor.generateContext(), serialize(nablarchSid, entries));
        MultipartContent content = createMultipartContent(file);
        MultipartContent.Part part = new MultipartContent.Part(new ByteArrayContent(null, DatatypeConverter.printBase64Binary(encrypted).getBytes()));
        part.setHeaders(new HttpHeaders().set("Content-Disposition", String.format("form-data; name=\"%s\"", "_HIDDEN_STORE_")));
        content.addPart(part);

        request = httpTransport.createRequestFactory()
                .buildPostRequest(new GenericUrl(new URL(baseUrl, "action/MultipartAction/GetSession")), content);
        request.getHeaders().set("Cookie", response.getHeaders().get("Set-Cookie"));
        response = request.execute();

        assertThat(response.getStatusCode(), is(200));
        assertThat(response.parseAsString(), is("value"));
    }

    /**
     * セッションID、セッションエントリのシリアライズを行う。
     * <p>
     * (HiddenStore#serializeと同一の処理)
     *
     * @param sessionId 現在のセッションID
     * @param entries   セッションエントリ
     * @return シリアライズ結果
     */
    private byte[] serialize(String sessionId, List<SessionEntry> entries) {
        // セッションID
        byte[] sidBytes = sessionId.getBytes(Charset.forName("UTF-8"));
        // セッションIDバイト長
        byte[] sidLengthBytes = ByteBuffer.allocate(Integer.SIZE / Byte.SIZE).putInt(sidBytes.length).array();
        // セッションエントリ
        byte[] entriesBytes = encode(entries);
        return concat(sidLengthBytes, sidBytes, entriesBytes);
    }

    /**
     * セッションエントリリストをエンコードする。
     *
     * @param entries セッションエントリリスト
     * @return バイト配列
     */
    private byte[] encode(List<SessionEntry> entries) {
        JavaSerializeEncryptStateEncoder<AesEncryptor.AesContext> stateEncoder = new JavaSerializeEncryptStateEncoder<AesEncryptor.AesContext>();
        stateEncoder.setEncryptor(aesEncryptor);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(2048);
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            for (SessionEntry entry : entries) {
                dos.writeUTF(entry.getKey());
                Object obj = entry.getValue();
                if (obj == null) {
                    dos.writeInt(0);
                } else {
                    byte[] encoded = stateEncoder.encode(obj);
                    dos.writeInt(encoded.length);
                    dos.writeUTF(obj.getClass().getName());
                    dos.write(encoded);
                }
            }
        } catch (IOException e) {
            throw new EncodeException(e);
        }
        return baos.toByteArray();
    }

    /**
     * バイト配列の結合を行う。
     *
     * @param byteArrays 結合対象のバイト配列
     * @return 結合後のバイト配列
     */
    private byte[] concat(byte[]... byteArrays) {
        int totalLenght = 0;
        for (byte[] bs : byteArrays) {
            totalLenght += bs.length;
        }
        ByteArrayOutputStream dest = new ByteArrayOutputStream(totalLenght);
        try {
            for (byte[] byteArray : byteArrays) {
                dest.write(byteArray);
            }
        } catch (IOException e) {
            throw new EncodeException(e);  // 発生しない
        } finally {
            FileUtil.closeQuietly(dest);
        }
        return dest.toByteArray();
    }
}
