public class ServerConfig {
    private String hotelsJsonPath;
    private String registeredUsersJsonFileName;
    private String reviewsJsonFileName;
    private int tcpPort;
    private int dim;
    private int threadpoolSize;
    private int multicastPort;
    private String multicastIP;
    private int rankingUpdaterDefaultWaitTime;
    private int dbUpdaterWaitTime;
    private int rmiPort;

    public String getHotelsJsonPath() {
        return hotelsJsonPath;
    }
    public void setHotelsJsonPath(String hotelsJsonPath) {
        this.hotelsJsonPath = hotelsJsonPath;
    }
    public String getRegisteredUsersJsonFileName() {
        return registeredUsersJsonFileName;
    }
    public void setRegisteredUsersJsonFileName(String registeredUsersJsonFileName) {
        this.registeredUsersJsonFileName = registeredUsersJsonFileName;
    }
    public String getReviewsJsonFileName() {
        return reviewsJsonFileName;
    }
    public void setReviewsJsonFileName(String reviewsJsonFileName) {
        this.reviewsJsonFileName = reviewsJsonFileName;
    }
    public int getTcpPort() {
        return tcpPort;
    }
    public void setTcpPort(int tcpPort) {
        this.tcpPort = tcpPort;
    }
    public int getDim() {
        return dim;
    }
    public void setDim(int dim) {
        this.dim = dim;
    }
    public int getThreadpoolSize() {
        return threadpoolSize;
    }
    public void setThreadpoolSize(int threadpoolSize) {
        this.threadpoolSize = threadpoolSize;
    }
    public int getMulticastPort() {
        return multicastPort;
    }
    public void setMulticastPort(int multicastPort) {
        this.multicastPort = multicastPort;
    }
    public String getMulticastIP() {
        return multicastIP;
    }
    public void setMulticastIP(String multicastIP) {
        this.multicastIP = multicastIP;
    }
    public int getRankingUpdaterDefaultWaitTime() {
        return rankingUpdaterDefaultWaitTime;
    }
    public void setRankingUpdaterDefaultWaitTime(int rankingUpdaterDefaultWaitTime) {
        this.rankingUpdaterDefaultWaitTime = rankingUpdaterDefaultWaitTime;
    }
    public int getDbUpdaterWaitTime() {
        return dbUpdaterWaitTime;
    }
    public void setDbUpdaterWaitTime(int dbUpdaterWaitTime) {
        this.dbUpdaterWaitTime = dbUpdaterWaitTime;
    }

    public int getRmiPort() {
        return rmiPort;
    }

    public void setRmiPort(int rmiPort) {
        this.rmiPort = rmiPort;
    }
}
