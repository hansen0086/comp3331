import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * log class
 */

public class receiverLog {
    private File file;

    public receiverLog(String filePathName) throws IOException {
        file = new File(filePathName);
        file.createNewFile();
    }


    public void receiveInfo(String content) throws IOException {
        content = content + "\n";
        FileWriter fw = new FileWriter(file, true);
        fw.write(content);
        fw.close();
    }


    public void receiveInfo(String str, int i) throws IOException {
        String content = "";
        content = content + str + "                 " + i;
        receiveInfo(content);
    }

    public void receiveInfo(String eventType, long startTime, String packetType, int seq, int len, int ack) throws IOException {
        long currentTime = System.currentTimeMillis() - startTime;
        String result = "" + String.format("%.2f", (double)currentTime / 1000);
        receiveInfo(eventType, result, packetType, seq + "  ", len + " ", ack + "");
    }


    public void receiveInfo(String... list) throws IOException {
        String content = "";
        int len = list.length;
        for (int i = 0; i < len; i++) {
            String str = list[i] + "        ";
            content = content + str;
        }
        receiveInfo(content);
    }

}
