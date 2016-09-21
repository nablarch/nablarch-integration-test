package nablarch.fw.web.upload;

import mockit.Mock;
import mockit.MockUp;

import java.io.IOException;

/**
 * {@link MultipartParser}のモッククラス。
 */
public class MockMultipartParser extends MockUp<MultipartParser> {
    @Mock
    private void write(PartInfo part) throws IOException {
        throw new IOException();
    }
}
