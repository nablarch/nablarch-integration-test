package nablarch.fw.web;

import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.MultipartContent;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.net.URL;

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

    @Deployment
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class)
                .setWebXML(new File("src/test/webapp/WEB-INF/new-handler-queue-web.xml"));
    }

    /**
     * マルチパートリクエスト時、Hiddenセッションに格納した情報が正しく復元できることを確認するケース。
     * <p/>
     * Hiddenセッションには既に以下の情報が格納されていることを前提とする。
     * <ul>
     *     <li>名前："key"</li>
     *     <li>値："value"</li>
     * </ul>
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
        MultipartContent content = createMultipartContent(file);
        MultipartContent.Part part = new MultipartContent.Part(new ByteArrayContent(null, "AANrZXkAAAAQABBqYXZhLmxhbmcuU3RyaW5njmDwr5d2mkeZFXpTJHZGxg==".getBytes()));
        part.setHeaders(new HttpHeaders().set("Content-Disposition", String.format("form-data; name=\"%s\"", "_HIDDEN_STORE_")));
        content.addPart(part);

        request = httpTransport.createRequestFactory()
                .buildPostRequest(new GenericUrl(new URL(baseUrl, "action/MultipartAction/GetSession")), content);
        request.getHeaders().set("Cookie", response.getHeaders().get("Set-Cookie"));
        response = request.execute();

        assertThat(response.getStatusCode(), is(200));
        assertThat(response.parseAsString(), is("value"));
    }
}

