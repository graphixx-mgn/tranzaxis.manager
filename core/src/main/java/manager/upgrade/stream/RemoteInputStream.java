package manager.upgrade.stream;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

public class RemoteInputStream extends InputStream implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Readable source;
    private byte buffer[];
    private int pos;
    private int exp;
    private final static int MAX_EXP = 6; // max fetch size = 2^MAX_EXP (64 KB)

    public RemoteInputStream(Readable source) {
        this.source = source;
    }

    @Override
    public int read() throws IOException {
        if (pos == -2) return -1;
        if (buffer == null || pos > buffer.length - 1) {
            buffer = source.read(1024 *(exp > MAX_EXP ? 1 << MAX_EXP : 1 << exp++)); // max 64 KB fetch
            pos = 0;
            if (buffer.length == 0) {
                pos = -2;
                return -1;
            }
        }
        return buffer[pos++] & 0xff;
    }

    @Override
    public void close() throws IOException {
        source.close();
    }
    
    @Override
    public int available() throws IOException {
        return source.available();
    }
}
