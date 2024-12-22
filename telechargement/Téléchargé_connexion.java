import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class connexion {  
    private static final String URL = "jdbc:oracle:thin:@localhost:1521/orcl";  
    private static final String USERNAME = "scott"; 
    private static final String PASSWORD = "tiger";  

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USERNAME, PASSWORD);  
    }

}
