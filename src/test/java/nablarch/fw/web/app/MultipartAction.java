package nablarch.fw.web.app;

import nablarch.common.web.session.SessionUtil;
import nablarch.fw.ExecutionContext;
import nablarch.fw.web.HttpRequest;
import nablarch.fw.web.HttpResponse;
import nablarch.fw.web.upload.PartInfo;

import java.io.File;

/**
 * マルチパートリクエスト用の業務Actionクラス。
 */
public class MultipartAction {

    /**
     * アップロードファイルを一時ディレクトリから移動する。
     *
     * @param request リクエスト
     * @param context 実行コンテキスト
     * @return レスポンス
     */
    public HttpResponse doUpload(HttpRequest request, ExecutionContext context) {
        PartInfo partInfo = request.getPart("uploadFile").get(0);
        partInfo.moveTo(new File(System.getProperty("java.io.tmpdir")), "uploadFile");

        return new HttpResponse().write("SUCCESS");
    }

    /**
     * セッションからパラメータを取得する。
     *
     * @param request リクエスト
     * @param context 実行コンテキスト
     * @return レスポンス
     */
    public HttpResponse doGetSession(HttpRequest request, ExecutionContext context) {
        String key = SessionUtil.get(context, "key");
        return new HttpResponse().write(key);
    }
}
