package nablarch.fw.web;

import static junit.framework.TestCase.fail;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;

import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpMediaType;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.MultipartContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import nablarch.core.util.Builder;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.test.api.ArquillianResource;

import nablarch.fw.web.upload.CustomMultipartContent;
import nablarch.fw.web.upload.MockMultipartParser;
import nablarch.test.support.log.app.OnMemoryLogWriter;

import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * ウェブアプリケーションにおける標準ハンドラ構成での結合テストをサポートするクラス。
 * <p/>
 * 旧・新ハンドラ構成で同一の結果となるケースについてはこのクラスに追加する。
 */
public abstract class WebHandlerQueueIntegrationTestSupport {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @ArquillianResource
    protected URL baseUrl;

    protected HttpTransport httpTransport = new NetHttpTransport();

    private PrintStream originalStandardOutput;
    private ByteArrayOutputStream bufferStandardOutput;

    @Before
    public void setUp() throws Exception {
        // アップロードファイルが作成されていれば削除する。
        final File file = new File(System.getProperty("java.io.tmpdir"), "uploadFile");
        if (file.exists()) {
            file.delete();
        }

        originalStandardOutput = System.out;
        bufferStandardOutput = new ByteArrayOutputStream();
        System.setOut(new PrintStream(bufferStandardOutput, true, StandardCharsets.UTF_8));
    }

    @After
    public void tearDown() {
        System.setOut(originalStandardOutput);
    }

    /**
     * src/test/resources 配下の全てのファイルを WebArchive の WEB-INF の下に配置する。
     * @param archive WebArchive
     */
    protected static void addTestResources(WebArchive archive) {
        Path srcTestResources = Path.of("src/test/resources");
        Path classes = Path.of("classes");

        try {
            Files.walkFileTree(srcTestResources, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    final Path relativePath = srcTestResources.relativize(file);
                    final Path targetPath = classes.resolve(relativePath);
                    archive.addAsWebInfResource(file.toFile(), targetPath.toString().replace('\\', '/'));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    
    /**
     * マルチパートリクエストが正しく処理されることを確認するケース。
     * @throws Exception
     */
    @Test
    @RunAsClient
    public void testMultipart_success() throws Exception {
        final File file = folder.newFile("multipart.txt");

        final com.google.api.client.http.HttpRequest request = httpTransport.createRequestFactory()
                                                                            .buildPostRequest(new GenericUrl(new URL(baseUrl, "action/MultipartAction/Upload")), createMultipartContent(file));

        final com.google.api.client.http.HttpResponse response = request.execute();

        assertThat(response.getStatusCode(), is(200));
        assertThat(response.parseAsString(), is("SUCCESS"));
        assertThat(new File(System.getProperty("java.io.tmpdir"), "uploadFile").exists(), is(true));
        assertLogContains("name='uploadFile', fileName='multipart.txt', contentType='application/octet-stream'");
    }

    /**
     * マルチパートリクエストにboundaryが設定されていない場合に、
     * BAD REQUESTが返却されることを確認するケース。
     * @throws Exception
     */
    @Test
    @RunAsClient
    public void testMultipart_nothing_boundary() throws Exception {
        final File file = folder.newFile("multipart.txt");
        try {
            final com.google.api.client.http.HttpRequest request = httpTransport.createRequestFactory()
                                                                                .buildPostRequest(new GenericUrl(new URL(baseUrl, "action/MultipartAction/Upload")), createCustomMultipartContent(file));
            request.execute();
            fail("boundaryが設定されていないため、BAD REQUEST(400)が送出される。");
        } catch (final HttpResponseException e) {
            assertThat(e.getStatusCode(), is(400));
        }
    }

    /**
     * マルチパートリクエストでファイルサイズが許容サイズよりも大きい場合に、
     * REQUEST ENTITY TOO LARGEが返却されることを確認するケース。
     * @throws Exception
     */
    @Test
    @RunAsClient
    public void testMultipart_too_large() throws Exception {
        final File file = createLargeFile("multipart.txt");
        try {
            final com.google.api.client.http.HttpRequest request = httpTransport.createRequestFactory()
                                                                                .buildPostRequest(new GenericUrl(new URL(baseUrl, "action/MultipartAction/Upload")), createMultipartContent(file));
            request.execute();
            fail("アップロードファイルサイズが上限を超えたため、REQUEST ENTITY TOO LARGE(413)が送出される。");
        } catch (final HttpResponseException e) {
            assertThat(e.getStatusCode(), is(413));
        }
    }

    // エラーを起こすためにモックをつかっているが、
    // サーバーを Wildfly にかえるとモック化ができずにエラーが発生しなくなった。
    // 可能性として、 Wildfly ではデプロイされたWebアプリケーションのクラスは
    // 別のクラスローダーでロードされるので、うまくモック化できない可能性が考えられる。
    // モックを使わずに同様のエラーを起こす方法がないため、テストを Ignore で除外している。
    // 将来 EE 10 に対応した GlassFish が Arquillian で使えるようになったときに Ignore を外してテストを実施すること。
    /**
     * マルチパートリクエストにて、一時ファイルの作成に失敗した場合、
     * INTERNAL SERVER ERRORが返却されることを確認するケース。
     * @throws Exception
     */
    @Test
    @RunAsClient
    @Ignore
    public void testMultipart_write_failed() throws Exception {

        // 一時ファイルの作成時にエラーが発生するようにモックを定義。
        new MockMultipartParser();

        final File file = folder.newFile("multipart.txt");
        try {
            final HttpRequest request = httpTransport.createRequestFactory()
                    .buildPostRequest(new GenericUrl(new URL(baseUrl, "action/MultipartAction/Upload")), createMultipartContent(file));

            request.execute();
            fail("一時ファイルの作成に失敗したため、INTERNAL SERVER ERROR(500)が送出される。");
        } catch (final HttpResponseException e) {
            assertThat(e.getStatusCode(), is(500));
            Thread.sleep(100); // ログ出力される前にassertされるのを防ぐため
            OnMemoryLogWriter.assertLogContains(
                    "writer.memory",
                    "FATAL",
                    "[500 InternalError] java.io.IOException");
        }
    }

    /**
     * {@link MultipartContent}を作成する。
     *
     * @param file アップロードファイル
     * @return {@link MultipartContent}
     * @throws IOException 入出力例外
     */
    protected MultipartContent createMultipartContent(final File file) throws IOException {
        final MultipartContent content = new MultipartContent();
        content.setMediaType(new HttpMediaType("multipart/form-data")
                .setParameter("boundary", "__END_OF_PART__"));

        // パラメータ
        MultipartContent.Part part = new MultipartContent.Part(new ByteArrayContent(null, "value".getBytes()));
        part.setHeaders(new HttpHeaders().set("Content-Disposition", String.format("form-data; name=\"%s\"", "key")));
        content.addPart(part);

        // ファイル
        part = new MultipartContent.Part(new FileContent("application/octet-stream", file));
        part.setHeaders(new HttpHeaders().set("Content-Disposition", String.format("form-data; name=\"uploadFile\"; filename=\"%s\"", file.getName())));
        return content.addPart(part);
    }

    /**
     * {@link CustomMultipartContent}を作成する。
     *
     * @param file アップロードファイル
     * @return {@link CustomMultipartContent}
     * @throws IOException 入出力例外
     */
    protected CustomMultipartContent createCustomMultipartContent(final File file) throws IOException {
        final CustomMultipartContent content = new CustomMultipartContent();
        content.setMediaType(new HttpMediaType("multipart/form-data"));

        final CustomMultipartContent.Part part = new CustomMultipartContent.Part(new FileContent("application/octet-stream", file));
        part.setHeaders(new HttpHeaders().set("Content-Disposition", String.format("form-data; name=\"uploadFile\"; filename=\"%s\"", file.getName())));
        return content.addPart(part);
    }

    /**
     * アップロードサイズの上限を超えるファイルを作成する。
     *
     * @param fileName アップロードファイル名
     * @return ファイル
     * @throws IOException 入出力例外
     */
    protected File createLargeFile(final String fileName) throws IOException {
        final File file = folder.newFile(fileName);
        final FileWriter filewriter = new FileWriter(file);
        for (int i = 0; i < 10000; i++) {
            filewriter.write("test");
        }
        filewriter.close();
        return file;
    }

    /**
     * 出力されたログに指定したメッセージが全て含まれることを検証する。
     * @param expectedMessages 出力されていることを期待するメッセージ
     */
    private void assertLogContains(String... expectedMessages) {
        String actualLogText = bufferStandardOutput.toString(StandardCharsets.UTF_8);

        for (String expectedMessage : expectedMessages) {
            if (!actualLogText.contains(expectedMessage)) {
                throw new AssertionError(Builder.concat(
                        "expected log not found. \n",
                        "expected = ", Arrays.toString(expectedMessages), "\n",
                        "actual   = ", actualLogText)
                );
            }
        }
    }
}
