import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
public class TestJsch {
    public static void main(String[] args) throws Exception {
        JSch jsch = new JSch();
        jsch.addIdentity("z:\\´ç±Ù\\ssh_key\\new\\id_rsa");
        Session session = jsch.getSession("comma", "192.168.1.30", 22);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(5000);
        System.out.println("SUCCESS JSch");
        session.disconnect();
    }
}
