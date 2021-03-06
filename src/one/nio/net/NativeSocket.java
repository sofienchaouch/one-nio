/*
 * Copyright 2015-2016 Odnoklassniki Ltd, Mail.Ru Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package one.nio.net;

import one.nio.mem.DirectMemory;
import one.nio.os.Mem;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;

class NativeSocket extends Socket {
    private static final int SOCKADDR_SIZE = 1 + 24;  // 1 byte size + 24 bytes max for IPv6 address

    int fd;

    NativeSocket(boolean datagram) throws IOException {
        this.fd = socket0(datagram);
    }

    NativeSocket(int fd) {
        this.fd = fd;
    }

    @Override
    public final boolean isOpen() {
        return fd >= 0;
    }

    @Override
    public NativeSocket accept() throws IOException {
        return new NativeSocket(accept0(false));
    }

    @Override
    public NativeSocket acceptNonBlocking() throws IOException {
        return new NativeSocket(accept0(true));
    }

    @Override
    public final InetSocketAddress getLocalAddress() {
        byte[] sockaddr = new byte[SOCKADDR_SIZE];
        getsockname(sockaddr);
        return makeAddress(sockaddr);
    }

    @Override
    public final InetSocketAddress getRemoteAddress() {
        byte[] sockaddr = new byte[SOCKADDR_SIZE];
        getpeername(sockaddr);
        return makeAddress(sockaddr);
    }

    private InetSocketAddress makeAddress(byte[] buffer) {
        byte[] address;
        if (buffer[0] == 8) {
            address = Arrays.copyOfRange(buffer, 5, 9);  // IPv4
        } else if (buffer[0] == 24) {
            address = Arrays.copyOfRange(buffer, 9, 25); // IPv6
        } else {
            return null;
        }

        int port = (buffer[3] & 0xff) << 8 | (buffer[4] & 0xff);

        try {
            return new InetSocketAddress(InetAddress.getByAddress(address), port);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    @Override
    public Socket sslWrap(SslContext context) throws IOException {
        return new NativeSslSocket(fd, (NativeSslContext) context, false);
    }

    @Override
    public Socket sslUnwrap() {
        return this;
    }

    @Override
    public SslContext getSslContext() {
        return null;
    }

    @Override
    public <T> T getSslOption(SslOption<T> option) {
        return null;
    }

    @Override
    public void connect(InetAddress address, int port) throws IOException {
        connect0(address.getAddress(), port);
    }

    @Override
    public void bind(InetAddress address, int port, int backlog) throws IOException {
        bind0(address.getAddress(), port);
    }

    @Override
    public native void listen(int backlog) throws IOException;

    @Override
    public native void close();

    @Override
    public native int writeRaw(long buf, int count, int flags) throws IOException;

    @Override
    public int send(ByteBuffer data, int flags, InetAddress address, int port) throws IOException {
        if (!data.isDirect()) {
            throw new UnsupportedOperationException();
        }

        long bufAddress = DirectMemory.getAddress(data) + data.position();
        int result = sendTo(bufAddress, data.remaining(), flags, address.getAddress(), port);
        if (result > 0) {
            data.position(data.position() + result);
        }
        return result;
    }

    @Override
    public native int write(byte[] data, int offset, int count, int flags) throws IOException;

    @Override
    public native void writeFully(byte[] data, int offset, int count) throws IOException;

    @Override
    public native int readRaw(long buf, int count, int flags) throws IOException;

    @Override
    public native int read(byte[] data, int offset, int count, int flags) throws IOException;

    @Override
    public InetSocketAddress recv(ByteBuffer buffer, int flags) throws IOException {
        if (!buffer.isDirect()) {
            throw new UnsupportedOperationException();
        }

        byte[] sockaddr = new byte[SOCKADDR_SIZE];
        long bufAddress = DirectMemory.getAddress(buffer) + buffer.position();
        int result = recvFrom(bufAddress, buffer.remaining(), flags, sockaddr);
        if (result <= 0) {
            return null;
        }
        buffer.position(buffer.position() + result);
        return makeAddress(sockaddr);
    }

    @Override
    public native void readFully(byte[] data, int offset, int count) throws IOException;

    @Override
    public long sendFile(RandomAccessFile file, long offset, long count) throws IOException {
        return sendFile0(Mem.getFD(file.getFD()), offset, count);
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        int bytes;
        if (dst.hasArray()) {
            bytes = read(dst.array(), dst.arrayOffset() + dst.position(), dst.remaining(), 0);
        } else {
            bytes = readRaw(DirectMemory.getAddress(dst) + dst.position(), dst.remaining(), 0);
        }
        dst.position(dst.position() + bytes);
        return bytes;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        int bytes;
        if (src.hasArray()) {
            bytes = write(src.array(), src.arrayOffset() + src.position(), src.remaining(), 0);
        } else {
            bytes = writeRaw(DirectMemory.getAddress(src) + src.position(), src.remaining(), 0);
        }
        src.position(src.position() + bytes);
        return bytes;
    }

    @Override
    public final native void setBlocking(boolean blocking);

    @Override
    public final native boolean isBlocking();

    @Override
    public final native void setTimeout(int timeout);

    @Override
    public final native int getTimeout();

    @Override
    public final native void setKeepAlive(boolean keepAlive);

    @Override
    public final native boolean getKeepAlive();

    @Override
    public final native void setNoDelay(boolean noDelay);

    @Override
    public final native boolean getNoDelay();

    @Override
    public final native void setTcpFastOpen(boolean tcpFastOpen);

    @Override
    public final native boolean getTcpFastOpen();

    @Override
    public final native void setDeferAccept(boolean deferAccept);

    @Override
    public final native boolean getDeferAccept();

    @Override
    public final native void setReuseAddr(boolean reuseAddr, boolean reusePort);

    @Override
    public final native boolean getReuseAddr();

    @Override
    public final native boolean getReusePort();

    @Override
    public final native void setRecvBuffer(int recvBuf);

    @Override
    public final native int getRecvBuffer();

    @Override
    public final native void setSendBuffer(int sendBuf);

    @Override
    public final native int getSendBuffer();

    @Override
    public final native void setTos(int tos);

    @Override
    public final native int getTos();

    @Override
    public native byte[] getOption(int level, int option);

    @Override
    public native boolean setOption(int level, int option, byte[] value);

    // PF_INET
    static native int socket0(boolean datagram) throws IOException;
    final native void connect0(byte[] address, int port) throws IOException;
    final native void bind0(byte[] address, int port) throws IOException;

    // PF_UNIX
    static native int socket1() throws IOException;
    final native void connect1(String path) throws IOException;
    final native void bind1(String path) throws IOException;

    final native int accept0(boolean nonblock) throws IOException;
    final native long sendFile0(int sourceFD, long offset, long count) throws IOException;
    final native void getsockname(byte[] buffer);
    final native void getpeername(byte[] buffer);
    final native int sendTo(long buf, int size, int flags, byte[] address, int port) throws IOException;
    final native int recvFrom(long buf, int maxSize, int flags, byte[] addrBuffer) throws IOException;
}
