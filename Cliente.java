import javax.swing.*;
import java.awt.event.*;
import java.net.*;
import java.io.*;
import java.util.*;

public class Cliente {

	public static void main(String[] args) {
		
		Marco_cliente M_C=new Marco_cliente();
		
		M_C.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}

}
class Marco_cliente extends JFrame{
	
	public Marco_cliente() {
		
		Lamina_cliente L_C=new Lamina_cliente();
		
		setVisible(true);
		
		setTitle("Prueba sockets");
		
		setBounds(300,200,300,325);
		
		add(L_C);
		
		addWindowListener(new EnvioOnline());
	}
}
//------------------------------------------ENVIO DE SEÑAL ONLINE----------------------------------------
class EnvioOnline extends WindowAdapter{
	
	public void windowOpened(WindowEvent e) {
		
		try {
			
			Socket mi_socket=new Socket("192.168.1.2",9999);
			
			Paquete_envio datos=new Paquete_envio();
			
			datos.setMensaje("online");

			ObjectOutputStream paquete=new ObjectOutputStream(mi_socket.getOutputStream());
			
			paquete.writeObject(datos);
			
			paquete.close();
			
			mi_socket.close();
		}catch(Exception e2) {
			
		}
	}
}
//-------------------------------------------------------------------------------------------------------
class Lamina_cliente extends JPanel implements Runnable{
	
	public Lamina_cliente() {
		
		JLabel nick2=new JLabel("nick: ");
		
		add(nick2);
		
		nick=new JLabel(JOptionPane.showInputDialog("Introduce nombre"));
		
		add(nick);
		
		JLabel texto=new JLabel("ONLINE:");
		
		add(texto);
		
		ip=new JComboBox();
					
		add(ip);
		
		campo1=new JTextField(20);
		
		mi_boton=new JButton("Enviar");
		
		Envia_texto mi_evento=new Envia_texto();
		
		mi_boton.addActionListener(mi_evento);
		
		campo_chat=new JTextArea(12,20);
		
		Thread mi_hilo=new Thread(this);

		mi_hilo.start();
		
		add(campo_chat);
		
		add(campo1);
		
		add(mi_boton);
	}
	private class Envia_texto implements ActionListener{

		@Override
		public void actionPerformed(ActionEvent e) {
			
			campo_chat.append("\n"+campo1.getText());
			
			try {
				Socket mi_socket=new Socket("192.168.1.2",9999);
				
				Paquete_envio datos=new Paquete_envio();
				
				datos.setNick(nick.getText());
				
				datos.setIp((String)ip.getSelectedItem());//para volverlo String tambien se puede poner .toString esa es otra forma.
				
				datos.setMensaje(campo1.getText());
				
				ObjectOutputStream paquete_datos=new ObjectOutputStream(mi_socket.getOutputStream());
				
				paquete_datos.writeObject(datos);
				
				paquete_datos.close();
				
				mi_socket.close();
			} catch (UnknownHostException e1) {
				e1.printStackTrace();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}	
	}	
	@Override
	public void run() {
		
		try {
			ServerSocket servidor_cliente=new ServerSocket(8888);
			
			Socket cliente;

			Paquete_envio paquete_recibido;

			while(true) {
				
				cliente=servidor_cliente.accept();
				
				ObjectInputStream flujo_entrada=new ObjectInputStream(cliente.getInputStream());

				paquete_recibido=(Paquete_envio) flujo_entrada.readObject();
				
				if(!paquete_recibido.getMensaje().equals("online")) {
					
					campo_chat.append("\n"+paquete_recibido.getNick()+":"+"\n "+paquete_recibido.getMensaje());
				}else {
					ArrayList<String> ip_menu=new ArrayList<String>();
					
					ip_menu=paquete_recibido.getIps();
					
					ip.removeAllItems();
					
					for (String z : ip_menu) {
						
						ip.addItem(z);
					}
				}			
				flujo_entrada.close();
			}
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}

	}	
	private JTextArea campo_chat;
	
	private JTextField campo1;
	
	private JComboBox ip;
	
	private JLabel nick;
	
	private JButton mi_boton;
}

class Paquete_envio implements Serializable{
	
	private static final long serialVersionUID = 1L;

	public Paquete_envio() {
		
	}
	public String getNick() {
		return nick;
	}

	public void setNick(String nick) {
		this.nick = nick;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public String getMensaje() {
		return mensaje;
	}

	public void setMensaje(String mensaje) {
		this.mensaje = mensaje;
	}
	
	public ArrayList<String> getIps() {
		return ips;
	}
	public void setIps(ArrayList<String> ips) {
		this.ips = ips;
	}

	private String nick,ip,mensaje;
	
	private ArrayList<String> ips;
}