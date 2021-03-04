import java.util.Objects;

public class Server implements Comparable<Server>{
    public String name;
    public double downloadSpeed;

    public Server(String name, double downloadSpeed) {
        this.name = name;
        this.downloadSpeed = downloadSpeed;
    }

    @Override
    public int compareTo(Server o) {
        return Double.compare(o.downloadSpeed, this.downloadSpeed);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Server server = (Server) o;
        return Double.compare(server.downloadSpeed, downloadSpeed) == 0 && Objects.equals(name, server.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, downloadSpeed);
    }

    @Override
    public String toString() {
        return name + "\t" + downloadSpeed;
    }
}
