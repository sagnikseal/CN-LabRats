import java.nio.ByteBuffer;

public class Packet {
	
	// constants
	private final static int BUFF_SIZE = 512;
	private final static int PACKET_SIZE = 528;
	//private final int SEQ_NUM = 32;
	
	// data members
	private int type;
	private int seqnum;
	private byte[] data;
    private String end = "    ";
	private String over = " \n\r";
    private String strt = "    ";
    
	//////////////////////// CONSTRUCTORS //////////////////////////////////////////

	// hidden constructor to prevent creation of invalid packets
	private Packet(String strStart, int SeqNum, byte[] rcvData, String endStr) throws Exception {
		strt = strStart;
        seqnum = SeqNum;
		data = rcvData;
        end = endStr;
	}
	
    private Packet(String strData) throws Exception{
        data = ("REQUEST " + strData + " \n\r").getBytes(); 
    }
        
    
    
	// special packet constructors to be used in place of hidden constructor
	public static Packet createACK(int SeqNum) throws Exception {
		return new Packet("ACK ", SeqNum, "".getBytes(),"    ");
	}
	
	public static Packet createPacket(String start, int SeqNum, byte[] fileData, String end) throws Exception {
		return new Packet(start, SeqNum, fileData, end);
	}
	
    public static Packet requestPacket(String request_data) throws Exception{
        return new Packet(request_data);
    }
	
	///////////////////////// PACKET DATA //////////////////////////////////////////
	
	public String getType() {
		return strt;
	}
	
    public String getEnd(){
        return end;
    }
	public int getSeqNum() {
		return seqnum;
	}
	
	public byte[] getData() {
		return data;
	}
	
	//////////////////////////// UDP HELPERS ///////////////////////////////////////
	
	public byte[] getUDPdata() {
		ByteBuffer buffer = ByteBuffer.allocate(PACKET_SIZE);
		buffer.put(strt.getBytes());
        buffer.putInt(seqnum);
        buffer.put(" ".getBytes());
        buffer.put(data);
        buffer.put(end.getBytes());
        buffer.put(over.getBytes());
		return buffer.array();
	}
	
    public byte[] getUDPRequest(){
        ByteBuffer buffer = ByteBuffer.allocate(PACKET_SIZE);
        buffer.put(data);
        return buffer.array();
    }
    
	public static Packet parseUDPdata(byte[] UDPdata) throws Exception {
		ByteBuffer buffer = ByteBuffer.wrap(UDPdata);
		byte[] strt = new byte[4];
        buffer.get(strt,0,4);
		int seqnum = buffer.getInt();
        byte[] space = new byte[1];
        buffer.get(space,0,1);
		byte data[] = new byte[BUFF_SIZE];
		buffer.get(data, 0, BUFF_SIZE);
        byte end[] = new byte[4];
        buffer.get(end,0,4);
		return new Packet(new String(strt), seqnum, data, new String(end));
	}
}