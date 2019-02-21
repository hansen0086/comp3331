import java.util.Arrays;

/**
 * transfer segment
 */

public class receiverSegment {

    private int synNumber;
    private int ackNumber;
    private int ACK;
    private int SYN;
    private int FIN;
    private int len;
    private byte checkNum;
    private byte[] data;


    public receiverSegment(){
    }

    public receiverSegment(int synNumber, int ackNumber, int ACK, int SYN, int FIN, byte[] data) {
        this.synNumber = synNumber;
        this.ackNumber = ackNumber;
        this.ACK = ACK;
        this.SYN = SYN;
        this.FIN = FIN;
        this.data = data;
        this.len=getDataLength(data);
    }
    public int getSynNumber() {
        return synNumber;
    }

    public void setSynNumber(int synNumber) {
        this.synNumber = synNumber;
    }

    public int getAckNumber() {
        return ackNumber;
    }

    public void setAckNumber(int ackNumber) {
        this.ackNumber = ackNumber;
    }

    public int getACK() {
        return ACK;
    }

    public void setACK(int ACK) {
        this.ACK = ACK;
    }

    public int getSYN() {
        return SYN;
    }

    public void setSYN(int SYN) {
        this.SYN = SYN;
    }

    public int getFIN() {
        return FIN;
    }

    public void setFIN(int FIN) {
        this.FIN = FIN;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public int getLen() {
        return len;
    }

    public void setLen(int len) {
        this.len = len;
    }

    public byte getCheckNum() {
        return checkNum;
    }
    public void setCheckNum(byte checkNum) {
        this.checkNum = checkNum;
    }


    private int getDataLength(byte[] data) {
        if(data==null){
            return 0;
        }else {
            return data.length;
        }
    }

    @Override
    public String toString() {
        return ""+ synNumber + ackNumber +SYN+ACK+FIN;
    }


    public byte [] getBytes(){
        byte[] sequenceArray = Receiver.IntTransToByte(synNumber);
        byte[] ackArray = Receiver.IntTransToByte(ackNumber);
        byte signal = (byte) ((ACK << 2) + (SYN << 1) + FIN);
        byte[] signalArray = {signal};
        byte[] lenArray = Receiver.IntTransToByte(len);
        byte[] checkArray = {checkNum};
        if(data != null) {
            return Receiver.getOneArray(sequenceArray, ackArray, signalArray, checkArray,lenArray, data);
        }
        return Receiver.getOneArray(sequenceArray, ackArray, signalArray, checkArray, lenArray);
    }

}
