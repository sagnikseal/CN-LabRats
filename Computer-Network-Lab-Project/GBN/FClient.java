import java.io.*;
import java.net.*;
import java.util.*;

class FClient
{
    private InetAddress ip;
    private int serverPort;
	private static FileOutputStream fos;
	private static DatagramSocket socket;
    private final static double LOSS_RATE = 0.3;
    private final static int WINDOW_SIZE = 4;
    private final static int PACKET_SIZE = 528;
    private final static int BUFF_SIZE = 512;
    private String end = "";
    private int nextSeqNum = 0;
    private int seq_no=0;
	private byte[] packetBuffer = new byte[PACKET_SIZE];
    static int isSent = 0;
	public FClient(InetAddress ip, Integer serverPort)
			throws IOException {
		this.ip = ip;
		this.serverPort = serverPort;
	}
    
    public static void main(String args[]) throws IOException, Exception {

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		// arguments checking
		if (args.length != 2) {
			System.out.println("Please enter java FClient IP serverPORT");
			System.exit(1);
		}
        
		InetAddress ip = InetAddress.getByName(args[0]);
		Integer serverPort = Integer.parseInt(args[1]);
        
        System.out.print("Enter the name of File to Receive : ");
        String fileName = br.readLine();
        System.out.println("\n");
        File file = new File("my"+fileName);
		// receiver constructor
		FClient client = new FClient(ip, serverPort);
        try{
            socket = new DatagramSocket();
            fos = new FileOutputStream(file);

            Packet reqp = Packet.requestPacket(fileName);
            byte[] requestBuffer = new byte[PACKET_SIZE];
            requestBuffer = reqp.getUDPRequest();
            DatagramPacket reqPacket = new DatagramPacket(requestBuffer,requestBuffer.length, ip, serverPort);
            socket.send(reqPacket);
            //start to recieve
            client.start();
        }catch(Exception e){
            System.out.println("Problem in Connection");
        }
        finally
        {
            // close the Datagram Socket
            socket.close();
        }
	}

	// main method to receive and send packets
	private void start() throws Exception {
		while (true) {

			// use Datagram Packet to receive packets from sender through the
			// Datagram Socket
			DatagramPacket receiverPacket = new DatagramPacket(packetBuffer, packetBuffer.length);
			try {
				socket.receive(receiverPacket);
			} catch (IOException e) {
				System.out.println("Receiver Socket I/O exception!!!");
			}
            
			Packet pp = Packet.parseUDPdata(packetBuffer);
            seq_no = pp.getSeqNum();
            end = pp.getEnd();
            if (seq_no == nextSeqNum) {
                if(end.equalsIgnoreCase(" END")){
                    
                    System.out.println("PACKET Received SEQ " + seq_no + " [" + System.currentTimeMillis() + "]");
                    isSent = sendACK((nextSeqNum + 1)%WINDOW_SIZE,true);
                    if(isSent == 1){
                        byte[] asd = pp.getData();
                        int count = 0;
                        for(int i = BUFF_SIZE - 1; i >= 0 ; i--){
                            if(asd[i] == '\0'){
                                count++;
                            }
                        }
                        for(int j = 0; j < (BUFF_SIZE - count); j++){
                            fos.write(asd[j]);
                        }
                        System.out.println("............FILE RECEIVED................");
                        System.exit(1);
                    }
                    
                }
                else{
                    System.out.println("PACKET Received SEQ " + seq_no + " [" + System.currentTimeMillis() +"]");
                    isSent = sendACK((nextSeqNum + 1)%WINDOW_SIZE,false);
                    if(isSent == 1){
                        fos.write(pp.getData());
                        nextSeqNum = (nextSeqNum + 1)%WINDOW_SIZE;
                    }
                }           
            } 
       /*     else {
                System.out.println("Packets NOT Received SEQ " + seq_no);
                if (nextSeqNum != 0)
                   sendACK(nextSeqNum - 1);
            }*/
		}
	}
	// send the ACKs to the sender using Datagram Packet
	private int sendACK(int seqNum, boolean ee) throws Exception {
        Random random = new Random();
        if (random.nextDouble() < LOSS_RATE){
            System.out.println("Acknowledgement not sent Due to RATE LOSS");
            return 0; 
        }
		Packet ack = Packet.createACK(seqNum);
		byte[] ackBuffer = new byte[PACKET_SIZE];
		ackBuffer = ack.getUDPdata();
		DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length, ip, serverPort);
		socket.send(ackPacket);
		System.out.println("ACK sent SEQ " + seqNum + " [" + System.currentTimeMillis() + "]");
        return 1;
	}
}
