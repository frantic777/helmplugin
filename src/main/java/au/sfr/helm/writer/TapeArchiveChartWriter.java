package au.sfr.helm.writer;

import hapi.chart.ChartOuterClass.ChartOrBuilder;
import org.kamranzafar.jtar.TarEntry;
import org.kamranzafar.jtar.TarHeader;
import org.kamranzafar.jtar.TarOutputStream;
import org.microbean.helm.chart.AbstractArchiveChartWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

/**
 * An {@link AbstractArchiveChartWriter} that saves {@link
 * ChartOrBuilder} objects to a {@linkplain
 * #TapeArchiveChartWriter(OutputStream) supplied
 * <code>OutputStream</code>} in <a
 * href="https://www.gnu.org/software/tar/manual/html_node/Standard.html">TAR
 * format</a>, using a {@link TarOutputStream} internally.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 */
public class TapeArchiveChartWriter extends AbstractArchiveChartWriter {


    /*
     * Instance fields.
     */


    /**
     * The {@link TarOutputStream} to write to.
     *
     * <p>This field is never {@code null}.</p>
     *
     * @see #TapeArchiveChartWriter(OutputStream)
     */
    private final TarOutputStream outputStream;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link org.microbean.helm.chart.TapeArchiveChartWriter}.
     *
     * @param outputStream the {@link OutputStream} to write to; must
     *                     not be {@code null} and should be buffered at some level
     * @see AbstractArchiveChartWriter#AbstractArchiveChartWriter()
     * @see TarOutputStream#TarOutputStream(OutputStream)
     */
    public TapeArchiveChartWriter(final OutputStream outputStream) {
        super();
        Objects.requireNonNull(outputStream);
        this.outputStream = new TarOutputStream(outputStream);
    }


    /*
     * Instance methods.
     */


    /**
     * Creates a new {@link TarHeader} and a {@link TarEntry} wrapping
     * it and writes it and the supplied {@code contents} to the
     * underlying {@link TarOutputStream}.
     *
     * @param context  the {@link Context} describing the write operation
     *                 in effect; must not be {@code null}
     * @param path     the path within a tape archive to write; interpreted
     *                 as being relative to the current chart path; must not be {@code
     *                 null} or {@linkplain String#isEmpty() empty}
     * @param contents the contents to write; must not be {@code null}
     * @throws IOException              if a write error occurs
     * @throws NullPointerException     if {@code context}, {@code path}
     *                                  or {@code contents} is {@code null}
     * @throws IllegalArgumentException if {@code path} {@linkplain
     *                                  String#isEmpty() is empty}
     */
    @Override
    protected void writeEntry(final Context context, final String path, final String contents) throws IOException {
        Objects.requireNonNull(context);
        Objects.requireNonNull(path);
        Objects.requireNonNull(contents);
        if (path.isEmpty()) {
            throw new IllegalArgumentException("path", new IllegalStateException("path.isEmpty()"));
        }

        final byte[] contentsBytes = contents.getBytes(StandardCharsets.UTF_8);
        final long size = contentsBytes.length;
        final TarHeader tarHeader = TarHeader.createHeader(new StringBuilder(context.get("path", String.class)).append(path).toString(), size, Instant.now().getEpochSecond(), false, 0755);
        final TarEntry tarEntry = new TarEntry(tarHeader);
        this.outputStream.putNextEntry(tarEntry);
        this.outputStream.write(contentsBytes);
        this.outputStream.flush();
    }

    /**
     * Closes this {@link org.microbean.helm.chart.TapeArchiveChartWriter} by closing its
     * underlying {@link TarOutputStream}.  This {@link
     * org.microbean.helm.chart.TapeArchiveChartWriter} cannot be used again.
     *
     * @throws IOException if there was a problem closing the
     *                     underlying {@link TarOutputStream}
     */
    @Override
    public void close() throws IOException {
        this.outputStream.close();
    }

}
