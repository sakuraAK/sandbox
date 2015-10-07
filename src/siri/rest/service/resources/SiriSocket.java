package siri.rest.service.resources;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Date;

public class SiriSocket {

	private Socket socket = null;
    private boolean abort = false;
    private String ip;
    private int port;
    private int reconnectWait = ZConsts.DEFAULT_RECONNECT_WAIT;
    private int flushInterval = ZConsts.DEFAULT_FLUSH_INTERVAL;
    private BufferedReader in = null;
    private PrintWriter out = null;
    private ZipWriter zipOut;
    private ZipReader zipIn;
    private ZDate lastFlush = null;
    private Thread myThread = null;
    private boolean connected = false, reset = false;
    private final ZBuffer<String> buffer;
    private final Object sync = new Object();
    private String charset = ZConsts.DEFAULT_CHARSET;
    private String fileName = null; // used to simulate socket input from file
    private long pongOffset = 0;
    private int monitorInterval = 0;
    private ZDate lastMonitor = null;

    public ZSocket() {
        // Use a threaded buffer to write to the socket
        buffer = new ZBuffer<>(this);
    }

    public int getReconnectWait() {
        return reconnectWait;
    }

    public void setReconnectWait(int reconnectWait) {
        this.reconnectWait = reconnectWait;
    }

    public void setFlushInterval(int flushInterval) {
        this.flushInterval = flushInterval;
    }

    public void setMonitorInterval(int monitorInterval) {
        this.monitorInterval = monitorInterval;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public boolean isConnected() {
        return connected;
    }

    /**
     * Call this method when you want to offset the current machine's clock when sending $PONG messages
     * in response to $PING. Usually called when there is a need to pong the time of another machine that was
     * previously synchronized with the current machine
     * @param pongOffset    value to add to the current machine's clock
     */
    public synchronized void setPongOffset(long pongOffset) {
        this.pongOffset = pongOffset;
    }

    public synchronized long getPongOffset() {
        return pongOffset;
    }

    /**
     * Initialize a client socket
     *
     * @param ip
     * @param port
     */
    public void init(String ip, int port) {
        this.ip = ip;
        this.port = port;
        socket = null;
        cleanup(false);
    }

    /**
     * Initialize a server-side socket
     *
     * @param socket the socket object returned by ServerSocket.accept()
     */
    public void init(Socket socket) {
        this.socket = socket;
        ip = socket.getRemoteSocketAddress().toString();
        port = 0;
    }

    /**
     * Initialize the object to read the input from a file simulating socket
     * input
     *
     * @param fileName
     */
    public void init(String fileName) {
        this.fileName = fileName;
        ip = null;
        port = 0;
    }
    
    /**
     * Initialize a dummy socket with no input, whose send method is overloaded to provide alternative output
     * @param bufferSize the output buffer will be set to this size
     */
    public void init(int bufferSize) {
        ip = null;
        port = 0;
        fileName = null;
        buffer.setTimeout(0);
        buffer.setMaxCapacity(bufferSize);
        buffer.execute();
        connected = true;
    }

    /**
     * Establish client socket connection
     *
     * @return true if succeeded
     */
    private boolean connect() {
        try {
            synchronized (sync) {
                if (fileName != null) {
                    in = new BufferedReader(new InputStreamReader(new FileInputStream(fileName), charset));
                } else {
                    // create a socket only if this is a client side socket. A server side socket already exists
                    if (port != 0) {
                        socket = new Socket(ip, port);
                    }
                    zipIn = new ZipReader(socket.getInputStream());
                    zipOut = new ZipWriter(socket.getOutputStream());
                    in = new BufferedReader(new InputStreamReader(zipIn, charset));
                    out = new PrintWriter(new OutputStreamWriter(zipOut, charset));
                }

                lastFlush = null;
                connected = true;
                reset = false;
                buffer.execute();
            }
            onConnect();
        } catch (Exception e) {
            onConnectFailed(e);
            return false;
        }
        return true;
    }

    /**
     * Execute connection and socket reading on a new thread
     */
    public void execute() {
        abort = connected = false;
        try {
            myThread = new Thread(this);
            myThread.start();
        } catch (Exception e) {
            onConnectFailed(e);
        }
    }

    private void disconnect(String reason) {
        synchronized (sync) {
            if (!connected) {
                return;
            }
            connected = false;
            try {
                in.close();
                if (out != null) {
                    out.flush();
                    out.close();
                }
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException ex) {
            }
            in = null;
            out = null;
            socket = null;
            if (buffer != null) {
                buffer.cleanup(false);
            }
        }
        onDisconnect(reason);
    }

    /**
     * The thread body
     */
    @Override
    public void run() {
        String s[];
        try {
            while (!abort) {
                String reason = null;
                if (connect()) {
                    try {
                        while (reason == null) {
                            String scLine = in.readLine();
                            if (scLine == null) {
                                reason = ZConsts.NULL_READ;
                            } else {
                                if (scLine.startsWith(ZConsts.PONG_COMMAND)) {
                                    s = scLine.split(",");
                                    ZDate pongTime = new ZDate(s[1], ZDate.FULL_TIMESTAMP);
                                    ZDate pingTime = new ZDate(s[2], ZDate.FULL_TIMESTAMP);
                                    long roundTrip = pingTime.elapsed();
                                    onPong(pingTime.diff(pongTime) + roundTrip / 2, pongTime, pingTime, roundTrip);
                                } else if (scLine.startsWith(ZConsts.PING_COMMAND)) {
                                    s = scLine.split(",");
                                    transmit(ZConsts.PONG_COMMAND + "," +
                                            ZDate.now().add(getPongOffset()).format(ZDate.FULL_TIMESTAMP) + "," +
                                            s[1], true);
                                    onPing();
                                } else if (scLine.equals(ZConsts.COMPRESS_REQ)) {
                                    zipIn.startDecompression();
                                    transmit(ZConsts.COMPRESS_ACK, true);
                                    onCompressReq();
                                } else if (scLine.equals(ZConsts.COMPRESS_ACK)) {
                                    zipOut.startCompression();
                                    onCompressAck();
                                } else {
                                    processLine(scLine);
                                }
                            }
                        }
                    } catch (Exception e) {
                        reason = e.getMessage();
                    }
                    disconnect((abort ? "aborted: " : "") + reason);
                }
                // A server side socket is executed only once without attempts to reconnect
                if (port == 0) {
                    abort = true;
                }
                // if connection failed or socket was disconnected by the other side wait set amount of time before reattempt
                if (!abort & !reset) {
                    try {
                        this.waitForReattempt();
                    } catch (InterruptedException e) {
                    }
                }
                reset = false;
            }
        } catch (Exception e) {
            disconnect("aborted: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Send a line on the output socket
     *
     * @param line line to send
     * @param flush force a flush after sending the line
     * @return true if sending the line was followed by flushing
     */
    public boolean send(String line, boolean flush) {
        synchronized (sync) {
            if (!connected || out == null) {
                return false;
            }
        }

        // as this method is called frequenly whether or not there is data to send, it's a good place
        // to do monitoring
        if (lastMonitor == null)
            lastMonitor = ZDate.now();
        if (monitorInterval > 0 && lastMonitor.elapsed() >= monitorInterval) {
            lastMonitor = ZDate.now();
            monitor();
        }
        
        boolean flushed = false;
        if (lastFlush == null) {
            lastFlush = ZDate.now();
        }
        try {
            if (line != null) {
                out.println(line);
            }
            if (flush || flushInterval > 0 && lastFlush.elapsed() >= flushInterval * 1000) {
                out.flush();
                lastFlush = ZDate.now();
                flushed = true;
            }
        } catch (Exception ex) {
            disconnect("On writing: " + ex.getMessage());
        }
        return flushed;
    }
  
    /**
     * Overload this if you want to do monitoring on this socket
     */
    protected void monitor() {}
    
    /**
     * Write a text line to the socket through the threaded buffer
     *
     * @param line the text line
     * @param urgent true if needs to be inserted into the head of the buffer
     * and be flushed to the socket
     * @return false if the buffer is full and had to clear it
     */
    public boolean transmit(String line, boolean urgent) {
        synchronized (sync) {
            if (!isConnected() || buffer == null) {
                return true;
            }
        }
        return urgent ? buffer.addUrgent(line) : buffer.add(line);
    }

    /**
     * Abort everything on cleanup
     * @param flush true if buffer shall be flushed before terminating
     */
    public void cleanup(boolean flush) {
        if (buffer != null) {
            buffer.cleanup(flush);
        }
        try {
            this.abort = true;
            if (myThread != null)
                myThread.interrupt();
            if (out != null) {
                out.flush();
                out.close();
                out = null;
            }
            if (socket != null) {
                socket.close();
            }
        } catch (Exception e) {
        }
    }

    /**
     * Reset the connection. Usually this is requested when ip address or port
     * number have been changed
     *
     * @param ip
     * @param port
     */
    public void reset(String ip, int port) {
        synchronized (sync) {
            try {
                if (ip != null) {
                    this.ip = ip;
                    this.port = port;
                    this.reset = true;
                    myThread.interrupt();
                    if (socket != null) {
                        socket.close();
                    }
                }
            } catch (Exception ex) {
            }
        }
    }
    
    /**
     * Called to signal that the output of this socket should be compressed (if the other peer approves)
     */
    public void compressOutput () {
        transmit(ZConsts.COMPRESS_REQ, true);
    }

    /**
     * If connection failed wait the specified amount of seconds and try again
     *
     * @throws InterruptedException
     */
    protected void waitForReattempt() throws InterruptedException {
        Thread.sleep(reconnectWait * 1000);
    }

    /**
     * Derived classes must implement this
     *
     * @param line
     */
    abstract protected void processLine(String line);

    public void ping() {
        transmit(ZConsts.PING_COMMAND + "," + ZDate.now(ZDate.FULL_TIMESTAMP), true);
    }

    /**
     * Override this if you want to do something when connection is established
     */
    protected void onConnect() {
    }

    /**
     * Override this if you want to do something when the socket is disconnected
     */
    protected void onDisconnect(String reason) {
    }

    /**
     * Override this if you want to do something else when connection failed
     *
     * @param e the exception that caused it to fail
     */
    protected void onConnectFailed(Exception e) {
        e.printStackTrace();
    }

    /**
     * Override this if you want to handle a received Pong command
     *
     * @param diff the clock difference from the other peer
     * @param pongTime the time of the pong command sent by the other peer
     * @param pingTime the time of the ping command sent by the current peer
     * @param roundTrip time elapsed between sending the ping and receiving the
     * pong
     */
    protected void onPong(long diff, Date pongTime, Date pingTime, long roundTrip) {
    }

    /**
     * Override this if you want to do something when the socket reads a ping
     * command
     */
    protected void onPing() {
    }
    
    /**
     * Override this if you want to do something when the socket gets a request to acknowledge receiving compressed
     * data
     */
    protected void onCompressReq() {
    }

    /**
     * Override this if you want to do something when the socket receives approval to start compressing data
     */
    protected void onCompressAck() {
        
    }
    
    /**
     * Dispense a line from the attached threaded buffer by writing it to the
     * socket
     *
     * @param line
     * @param flush true if should flush the writer buffer to send immediately
     */
    @Override
    public void dispense(String line, boolean flush) {
        send(line, flush);
    }
}
