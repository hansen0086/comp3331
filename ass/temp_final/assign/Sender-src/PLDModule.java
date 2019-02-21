
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

/**
 * PLDModule module
 */

public class PLDModule {

    private DatagramSocket clientSocket;
    private senderLog senderLog;
    private long startTime;
    private String hostIp;
    private int receiverPort;
    private Random random;
    private int orderNo;


    private int dropAmount = 0;
    private int duplicatedAmout = 0;
    private int corruptedAmount = 0;
    private int reorderAmount = 0;
    private int delayAmount = 0;
    private Timer timer;
    private sendSegment orderSegment;


    public PLDModule(DatagramSocket soc, senderLog log, Long startTime, String hostIp, int receiverPort, int seed) {
        this.clientSocket = soc;
        this.senderLog = log;
        this.startTime = startTime;
        this.hostIp = hostIp;
        this.receiverPort = receiverPort;
        random = new Random(seed);

        timer = new Timer();
        orderNo = 0;
    }

    public void dealSegment(sendSegment segment, String receiver_host_ip, int receiver_port, int seed, float drop, float duplicate, float corrupt, float order, float delay, int maxOrder, int maxDelay, String snd, String data) throws IOException {

        // judge segment
        float pDrop = random.nextFloat();
        float pDuplicate = random.nextFloat();
        float pCorrupt = random.nextFloat();
        float pOrder = random.nextFloat();
        float pDelay = random.nextFloat();
        if(drop - pDrop > Sender.EXP){
            this.dropAmount++;
            senderLog.sendInfo(Sender.DROP, startTime,Sender.DATA, segment.getSynNumber(), segment.getLen(), segment.getAckNumber());
        }else if(duplicate - pDuplicate > Sender.EXP){
            duplicatedAmout++;
            sendSegment(clientSocket, segment, Sender.SND, Sender.DATA);
            sendSegment(clientSocket, segment, Sender.DUP, Sender.DATA);
        }else if(order - pOrder > Sender.EXP){
            if(orderSegment == null){
                this.reorderAmount++;
                orderSegment = segment;
                orderNo = 0;
                senderLog.sendInfo(Sender.RORD, startTime, Sender.DATA, segment.getSynNumber(), segment.getLen(), segment.getAckNumber());
            }else{
                sendSegment(clientSocket,segment, Sender.SND, Sender.DATA);
            }
        }else if(delay - pDelay > Sender.EXP){
            delayAmount++;
            senderLog.sendInfo(Sender.DELY, startTime, Sender.DATA, segment.getSynNumber(), segment.getLen(), segment.getAckNumber());
            Random delayRandom = new Random();
            int delayTime = delayRandom.nextInt(maxDelay);

            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        sendSegment(clientSocket, segment, Sender.SND, Sender.DATA);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, delayTime);
        }else{
            sendSegment(clientSocket,segment, Sender.SND, Sender.DATA);
        }

        orderNo++;
        if(orderSegment != null && orderNo > maxOrder){
            sendSegment(clientSocket, orderSegment, Sender.SND, Sender.DATA);
            orderSegment = null;
            orderNo = 0;
        }
    }

    private void sendSegment(DatagramSocket senderSocket, sendSegment segment, String eventType, String packetType) throws IOException {
        InetAddress IPAddress = InetAddress.getByName(hostIp);
        DatagramPacket sendPacket = new DatagramPacket(segment.getBytes(),segment.getBytes().length, IPAddress, receiverPort);
        senderSocket.send(sendPacket);
        senderLog.sendInfo(eventType, startTime, packetType, segment.getSynNumber(), segment.getLen(), segment.getAckNumber());
    }

    public void close(){
        timer.cancel();
    }

    public int getDropAmount() {
        return this.dropAmount;
    }

    public int getDuplicatedAmout() {
        return this.duplicatedAmout;
    }

    public int getCorruptedAmount() {
        return this.corruptedAmount;
    }

    public int getReorderAmount() {
        return this.reorderAmount;
    }

    public int getDelayAmount() {
        return this.delayAmount;
    }
}
