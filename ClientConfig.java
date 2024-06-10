public class ClientConfig {
    private int tcpPort;
    private int rmiPort;
    private String serverIP;
    private int dim;
    public int multicastPort;
    public String multicastIP;

    public int getTcpPort() {
        return tcpPort;
    }
    public void setTcpPort(int tcpPort) {
        this.tcpPort = tcpPort;
    }
    public int getRmiPort() {
        return rmiPort;
    }
    public void setRmiPort(int rmiPort) {
        this.rmiPort = rmiPort;
    }
    public String getServerIP() {
        return serverIP;
    }
    public void setServerIP(String serverIP) {
        this.serverIP = serverIP;
    }
    public int getDim() {
        return dim;
    }
    public void setDim(int dim) {
        this.dim = dim;
    }
    public String getMulticastIP() {
        return multicastIP;
    }
    public void setMulticastIP(String multicastIP) {
        this.multicastIP = multicastIP;
    }
    public int getMulticastPort() {
        return multicastPort;
    }
    public void setMulticastPort(int multicastPort) {
        this.multicastPort = multicastPort;
    }
}
