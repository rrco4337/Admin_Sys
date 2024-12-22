public class SubServerInfo {
    public String host;
    public int port;
    public String storagePath;
    public SubServerInfo(String host, int port, String storagePath) {
        this.host = host;
        this.port = port;
        this.storagePath = storagePath;
    }
    @Override
    public String toString() {
        return host + ":" + port + " [" + storagePath + "]";
    }
}
