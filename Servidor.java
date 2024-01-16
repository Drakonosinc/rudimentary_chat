import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.io.*;
import java.util.*;

public class Servidor {

	public static void main(String[] args) {
		
		Marco_servidor M_S=new Marco_servidor();
		
		M_S.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}

}
class Marco_servidor extends JFrame{
	
	public Marco_servidor() {
		
		Lamina_servidor L_S=new Lamina_servidor();
		
		setVisible(true);
		
		setTitle("Prueba servidor");
		
		setBounds(800,200,300,300);
		
		Thread mi_hilo=new Thread(L_S);
		
		mi_hilo.start();
		
		add(L_S);
	}
}
class Lamina_servidor extends JPanel implements Runnable{
	
	public Lamina_servidor() {
		setLayout(new BorderLayout());
		
		JPanel lamina1=new JPanel();
				
		lamina1.setLayout(new BorderLayout());
		
		area_texto=new JTextArea();
		
		lamina1.add(area_texto,BorderLayout.CENTER);
			
		add(lamina1);
	}
	@Override
	public void run() {
		try {
			ServerSocket servidor=new ServerSocket(9999);
		
			String nick,ip,mensaje;
			
			ArrayList<String> listaip=new ArrayList<String>();
			
			Paquete_envio paquete_recibido;
			
			while(true){
				
			Socket mi_socket=servidor.accept();
					
			ObjectInputStream paquete_datos=new ObjectInputStream(mi_socket.getInputStream());
			
			paquete_recibido=(Paquete_envio) paquete_datos.readObject();
			
			nick=paquete_recibido.getNick();
			
			ip=paquete_recibido.getIp();
			
			mensaje=paquete_recibido.getMensaje();
			
			paquete_datos.close();
			
			if(!mensaje.equals("online")) {
		
			area_texto.append("\n"+nick+":"+"\n "+mensaje+"\n"+" para: "+ip);
			
			Socket envia_destinatario=new Socket(ip,8888);
			
			ObjectOutputStream paquete_reenvio=new ObjectOutputStream(envia_destinatario.getOutputStream());
			
			paquete_reenvio.writeObject(paquete_recibido);
			
			paquete_reenvio.close();
			
			envia_destinatario.close();
			
			mi_socket.close();          
			}else {		
//----------------------------DETECTA USUARIOS-----------------------------------------------------------
				
			InetAddress localizacion=mi_socket.getInetAddress();
				
			String ip_remota=localizacion.getHostAddress();
								
			listaip.add(ip_remota);
			
			paquete_recibido.setIps(listaip);
			
			for (String z : listaip) {
				
				Socket envia_destinatario=new Socket(z,8888);
				
				ObjectOutputStream paquete_reenvio=new ObjectOutputStream(envia_destinatario.getOutputStream());
				
				paquete_reenvio.writeObject(paquete_recibido);
				
				paquete_reenvio.close();
				
				envia_destinatario.close();
			}
//-------------------------------------------------------------------------------------------------------	
			}
			}
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
	}
	private JTextArea area_texto;
}