package cn.banny.emulator.linux.file;

import cn.banny.auxiliary.Inspector;
import cn.banny.emulator.Emulator;
import com.sun.jna.Pointer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import unicorn.Unicorn;

import java.io.IOException;
import java.net.InetAddress;

public class UdpSocket extends SocketIO implements FileIO {

    private static final Log log = LogFactory.getLog(UdpSocket.class);

    @Override
    public void close() {
    }

    @Override
    public int write(byte[] data) {
        throw new AbstractMethodError();
    }

    @Override
    public int read(Unicorn unicorn, Pointer buffer, int count) {
        throw new AbstractMethodError();
    }

    @Override
    public int fstat(Emulator emulator, Unicorn unicorn, Pointer stat) {
        throw new AbstractMethodError();
    }

    @Override
    public FileIO dup2() {
        return new UdpSocket();
    }

    @Override
    public int sendto(byte[] data, int flags, Pointer dest_addr, int addrlen) {
        if (addrlen != 16) {
            throw new IllegalStateException("addrlen=" + addrlen);
        }

        if (log.isDebugEnabled()) {
            byte[] addr = dest_addr.getByteArray(0, addrlen);
            Inspector.inspect(addr, "addr");
        }

        int sa_family = dest_addr.getInt(0);
        if (sa_family != AF_INET) {
            throw new AbstractMethodError("sa_family=" + sa_family);
        }

        try {
            InetAddress address = InetAddress.getByAddress(dest_addr.getByteArray(4, 4));
            throw new UnsupportedOperationException("address=" + address);
        } catch (IOException e) {
            return -1;
        }
    }

    @Override
    void setKeepAlive(int keepAlive) {
        throw new AbstractMethodError();
    }

    @Override
    void setSocketRecvBuf(int recvBuf) {
        throw new AbstractMethodError();
    }

    @Override
    void setReuseAddress(int reuseAddress) {
        throw new AbstractMethodError();
    }

    @Override
    void setTcpNoDelay(int tcpNoDelay) {
        throw new AbstractMethodError();
    }

    @Override
    int getTcpNoDelay() {
        throw new AbstractMethodError();
    }
}
