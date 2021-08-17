import java.io.*;
import java.net.*;
import java.util.*;

class FServer{
    
	private static int Sf = 0;
	private static int nextSeqNum = 0;
    private final static int WINDOW_SIZE = 4;
    private static int lastSeq = 9999999;
    private final static int TIME_OUT = 30;
    private final static double LOSS_RATE = 0.3;
    private final static int BUFF_SIZE = 512;
    private final static int PACKET_SIZE = 528;
    private static int sm1 = 0, sm2 = 0;
    private static FileInputStream fis = null;
    private static InetAddress ip = null;
    private static int clientPort = 0;
	private static boolean end = false;
    private static DatagramSocket socket = null;
	private static List<Packet> cache = new LinkedList<Packet>();

	// lock used to keep mutual exclusive
	private static Object lock = new Object();

    public static void main(String[] args) throws Exception {
		if (args.length != 1) {
			System.out.println("Please enter ServerPORT");
			System.exit(1);
		}

		Integer serverPort = Integer.parseInt(args[0]);
        System.out.println("\nWaiting for Client...\n\n");
        
        try{
            socket = new DatagramSocket(serverPort);
            byte[] packetBuffer = new byte[PACKET_SIZE];
           
            DatagramPacket rp = new DatagramPacket(packetBuffer, packetBuffer.length);
			try {
				socket.receive(rp);
                String str = new String(rp.getData());
                String[] strArray = str.split("\\s");
                String fn = strArray[1];
                System.out.println("File Name : " + fn);
                File file = new File(fn);
                fis = new FileInputStream(file);
                ip = rp.getAddress();
                clientPort = rp.getPort();
                
			} catch (IOException e) {
				System.out.println("Receiver Socket I/O exception!!!");
                System.exit(1);
			}
            
            // thread used to send packets to senders
            Thread sendPacketsThread = new Thread(new Runnable(){
                @Override
                public void run() {
                    try {
                        sendPackets(fis, ip, clientPort, socket);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });


            // thread used to receive ACKs sent by receiver and process them
            Thread monitorACKsThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        monitorACKs(ip, clientPort, socket);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

            // start the threads
            sendPacketsThread.start();
            monitorACKsThread.start();

            // wait for threads finish
            sendPacketsThread.join();
            monitorACKsThread.join();
        }catch(Exception e){
            System.out.println("Problem In Connection");
        }
		finally
        {
            // close the sender socket
            socket.close();
        }
	}

	private static void sendPackets(FileInputStream fis, InetAddress ip, int clientPort, DatagramSocket socket) throws Exception {
        Random random = new Random();
		try {

			Packet pp;
			while (true) {
                
                byte[] buffer = new byte[BUFF_SIZE];
				if (cache.size() >=WINDOW_SIZE){
           //         System.out.println("Window Full " + nextSeqNum + " : " + Sf);
                    continue;
                }

				// here should be mutex
				synchronized (lock) {
                        if (random.nextDouble() < LOSS_RATE){
          //              System.out.println("Packet not sent Due to RATE LOSS");
                        continue; 
                    }
					// input file packet by packet
					int ret = fis.read(buffer);
                    
					// last Packet
					if (ret < BUFF_SIZE) {
						pp = Packet.createPacket("RDT ", nextSeqNum, buffer, " END");
                        
						DatagramPacket senderPacket = new DatagramPacket(pp.getUDPdata(), pp.getUDPdata().length, ip, clientPort);
						socket.send(senderPacket);
						
                        // add the sent packet to cache as unACKed
                        cache.add(pp);
                        
						// close fileinputstream
						fis.close();
                  		System.out.println("PACKET SENT SEQ_NUM:" + pp.getSeqNum() + " [" + System.currentTimeMillis() + "]");
                        nextSeqNum = (nextSeqNum + 1)%WINDOW_SIZE;
                        sm1++;
                        lastSeq = sm1;
                        break; //this will end sending the file
					} 
                    else{                        
						// else just send data packets
						pp = Packet.createPacket("RDT ", nextSeqNum, buffer, "    ");
						
                        DatagramPacket senderPacket = new DatagramPacket(pp.getUDPdata(), pp.getUDPdata().length, ip, clientPort);
						socket.send(senderPacket);

						// add the sent packet to cache as unACKed
                        cache.add(pp);
				    
                        // update nextSeqNum
						nextSeqNum = (nextSeqNum + 1)%WINDOW_SIZE;
                        sm1++;
                        System.out.println("PACKET SENT SEQ_NUM: " + pp.getSeqNum() +" [" + System.currentTimeMillis() + "]");
					}
				}
			}
		}catch (SocketException e) {}
        catch (IOException e) {}
	}

	private static void monitorACKs(InetAddress ip, int clientPort,	DatagramSocket socket) throws Exception{

		while (true) {
			try{
                socket.setSoTimeout(TIME_OUT);
                
                byte[] buffer = new byte[PACKET_SIZE];
                DatagramPacket ACKs = new DatagramPacket(buffer, buffer.length);		
                socket.receive(ACKs);
                Packet ACKPackets = Packet.parseUDPdata(ACKs.getData());
                int ackNo = (ACKPackets.getSeqNum() - 1) >= 0 ? ((ACKPackets.getSeqNum() - 1)%WINDOW_SIZE):((ACKPackets.getSeqNum() - 1) + WINDOW_SIZE);
                System.out.println("RECEIVED ACK : " + (ackNo + 1) + " [" + System.currentTimeMillis() + "]");
                synchronized (lock) {
					if((ackNo) >= Sf)
                    {
                        int temp = ((ackNo - Sf) >= 0) ? ((ackNo - Sf)%WINDOW_SIZE):((ackNo - Sf) + WINDOW_SIZE);
                        //System.out.println(ackNo + "-" + Sf + "=" + temp);
                        for(int i = 0; i <= temp ; i++){
                           // System.out.println("Removing Sf " + Sf);
                            cache.remove(0);
                            sm2++;
                            Sf = (Sf + 1)%WINDOW_SIZE;  
                            if(sm2 == lastSeq){
                                System.out.println("............File SENT.............");
                                System.exit(1);
                            }
                        }
                        
                    }
				}
			} catch (SocketTimeoutException ex) {
				// while timeout resend all the packets in the cache
				//should be mutual exclusive
				synchronized (lock) {
                    for (int i = 0; i < cache.size(); i++) {
						DatagramPacket dp = new DatagramPacket
                            (cache.get(i).getUDPdata(),cache.get(i).getUDPdata().length, ip, clientPort);
						socket.send(dp);
						//For Debug
						 System.out.println("TIMEOUT resend PACKET SEQ_NO: " + cache.get(i).getSeqNum() + " [" + System.currentTimeMillis() + "]");
					}
				}
			}
            catch(Exception e){
                e.printStackTrace();
            }
		}
	}
}