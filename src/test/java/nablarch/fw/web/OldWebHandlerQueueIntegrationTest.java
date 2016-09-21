package nablarch.fw.web;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.runner.RunWith;

import java.io.File;

/**
 * ウェブアプリケーションにおけるNablarch5より前の標準ハンドラ構成で、
 * Nablarchが正しく動作することを確認する結合テストクラス。
 * <p/>
 * 新ハンドラ構成と比べて異なる結果となるケースをこのクラスに追加していく。
 */
@RunWith(Arquillian.class)
public class OldWebHandlerQueueIntegrationTest extends WebHandlerQueueIntegrationTestSupport {

    @Deployment
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class)
                .setWebXML(new File("src/test/webapp/WEB-INF/old-handler-queue-web.xml"));
    }
}