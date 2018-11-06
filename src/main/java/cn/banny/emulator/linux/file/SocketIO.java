package cn.banny.emulator.linux.file;

import com.sun.jna.Pointer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.net.SocketException;

public abstract class SocketIO extends AbstractFileIO implements FileIO {

    private static final Log log = LogFactory.getLog(SocketIO.class);

    public static final int AF_UNSPEC = 0;
    public static final int AF_LOCAL = 1;
    public static final int AF_INET = 2;

    public static final int SOCK_STREAM = 1;
    public static final int SOCK_DGRAM = 2;
    public static final int SOCK_RAW = 3;

    private static final int IPPROTO_IP = 0;
    public static final int IPPROTO_ICMP = 1;
    static final int IPPROTO_TCP = 6;

    private static final int SOL_SOCKET = 1;

    private static final int SO_REUSEADDR = 2;
    private static final int SO_BROADCAST = 6;
    private static final int SO_RCVBUF = 8;
    private static final int SO_KEEPALIVE = 9;

    static final int SHUT_RD = 0;
    static final int SHUT_WR = 1;
    static final int SHUT_RDWR = 2;

    private static final int TCP_NODELAY = 1;

    SocketIO() {
        super(O_RDWR);
    }

    @Override
    public int getsockopt(int level, int optname, Pointer optval, Pointer optlen) {
        try {
            switch (level) {
                case IPPROTO_TCP:
                    switch (optname) {
                        case TCP_NODELAY:
                            optlen.setInt(0, 4);
                            optval.setInt(0, getTcpNoDelay());
                            return 0;
                    }
                    break;
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return super.getsockopt(level, optname, optval, optlen);
    }

    abstract int getTcpNoDelay() throws SocketException;

    @Override
    public int setsockopt(int level, int optname, Pointer optval, int optlen) {
        try {
            switch (level) {
                case SOL_SOCKET:
                    switch (optname) {
                        case SO_REUSEADDR:
                            if (optlen != 4) {
                                throw new IllegalStateException("optlen=" + optlen);
                            }
                            setReuseAddress(optval.getInt(0));
                            return 0;
                        case SO_BROADCAST:
                            if (optlen != 4) {
                                throw new IllegalStateException("optlen=" + optlen);
                            }
                            optval.getInt(0); // broadcast_pings
                            return 0;
                        case SO_RCVBUF:
                            if (optlen != 4) {
                                throw new IllegalStateException("optlen=" + optlen);
                            }
                            setSocketRecvBuf(optval.getInt(0));
                            return 0;
                        case SO_KEEPALIVE:
                            if (optlen != 4) {
                                throw new IllegalStateException("optlen=" + optlen);
                            }
                            setKeepAlive(optval.getInt(0));
                            return 0;
                    }
                    break;
                case IPPROTO_TCP:
                    switch (optname) {
                        case TCP_NODELAY:
                            if (optlen != 4) {
                                throw new IllegalStateException("optlen=" + optlen);
                            }
                            setTcpNoDelay(optval.getInt(0));
                            return 0;
                    }
                    break;
                case IPPROTO_IP:
                    return 0;
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        log.warn("setsockopt level=" + level + ", optname=" + optname + ", optval=" + optval + ", optlen=" + optlen);
        return 0;
    }

    abstract void setTcpNoDelay(int tcpNoDelay) throws SocketException;

    abstract void setReuseAddress(int reuseAddress) throws SocketException;

    abstract void setKeepAlive(int keepAlive) throws SocketException;

    abstract void setSocketRecvBuf(int recvBuf) throws SocketException;

}
