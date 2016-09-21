package nablarch.fw.web.upload;

import com.google.api.client.http.AbstractHttpContent;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpEncoding;
import com.google.api.client.http.HttpEncodingStreamingContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.MultipartContent;
import com.google.api.client.util.Preconditions;
import com.google.api.client.util.StreamingContent;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * マルチパートリクエスト時に、境界文字列(boundary)を付与しない{@link MultipartContent}。
 */
public class CustomMultipartContent extends MultipartContent {

    static final String NEWLINE = "\r\n";

    private static final String TWO_DASHES = "--";

    /** Parts of the HTTP multipart request. */
    private ArrayList<CustomMultipartContent.Part> parts = new ArrayList<CustomMultipartContent.Part>();

    public void writeTo(OutputStream out) throws IOException {
        Writer writer = new OutputStreamWriter(out, getCharset());
        for (Part part : parts) {
            HttpHeaders headers = new HttpHeaders().setAcceptEncoding(null);
            if (part.headers != null) {
                headers.fromHttpHeaders(part.headers);
            }
            headers.setContentEncoding(null)
                    .setUserAgent(null)
                    .setContentType(null)
                    .setContentLength(null)
                    .set("Content-Transfer-Encoding", null);
            // analyze the content
            HttpContent content = part.content;
            StreamingContent streamingContent = null;
            if (content != null) {
                headers.set("Content-Transfer-Encoding", Arrays.asList("binary"));
                headers.setContentType(content.getType());
                HttpEncoding encoding = part.encoding;
                long contentLength;
                if (encoding == null) {
                    contentLength = content.getLength();
                    streamingContent = content;
                } else {
                    headers.setContentEncoding(encoding.getName());
                    streamingContent = new HttpEncodingStreamingContent(content, encoding);
                    contentLength = AbstractHttpContent.computeLength(content);
                }
                if (contentLength != -1) {
                    headers.setContentLength(contentLength);
                }
            }
            // write separator
            writer.write(TWO_DASHES);
            writer.write("__END_OF_PART__");
            writer.write(NEWLINE);
            // write headers
            HttpHeaders.serializeHeadersForMultipartRequests(headers, null, null, writer);
            // write content
            if (streamingContent != null) {
                writer.write(NEWLINE);
                writer.flush();
                streamingContent.writeTo(out);
                writer.write(NEWLINE);
            }
        }
        // write end separator
        writer.write(TWO_DASHES);
        writer.write("__END_OF_PART__");
        writer.write(TWO_DASHES);
        writer.write(NEWLINE);
        writer.flush();
    }

    public CustomMultipartContent addPart(Part part) {
        parts.add(Preconditions.checkNotNull(part));
        return this;
    }

    /**
     * Single part of a multi-part request.
     *
     * <p>
     * Implementation is not thread-safe.
     * </p>
     */
    public static final class Part {

        /** HTTP content or {@code null} for none. */
        HttpContent content;

        /** HTTP headers or {@code null} for none. */
        HttpHeaders headers;

        /** HTTP encoding or {@code null} for none. */
        HttpEncoding encoding;

        public Part() {
            this(null);
        }

        /**
         * @param content HTTP content or {@code null} for none
         */
        public Part(HttpContent content) {
            this(null, content);
        }

        /**
         * @param headers HTTP headers or {@code null} for none
         * @param content HTTP content or {@code null} for none
         */
        public Part(HttpHeaders headers, HttpContent content) {
            setHeaders(headers);
            setContent(content);
        }

        /** Sets the HTTP content or {@code null} for none. */
        public Part setContent(HttpContent content) {
            this.content = content;
            return this;
        }

        /** Returns the HTTP content or {@code null} for none. */
        public HttpContent getContent() {
            return content;
        }

        /** Sets the HTTP headers or {@code null} for none. */
        public Part setHeaders(HttpHeaders headers) {
            this.headers = headers;
            return this;
        }

        /** Returns the HTTP headers or {@code null} for none. */
        public HttpHeaders getHeaders() {
            return headers;
        }

        /** Sets the HTTP encoding or {@code null} for none. */
        public Part setEncoding(HttpEncoding encoding) {
            this.encoding = encoding;
            return this;
        }

        /** Returns the HTTP encoding or {@code null} for none. */
        public HttpEncoding getEncoding() {
            return encoding;
        }
    }
}
