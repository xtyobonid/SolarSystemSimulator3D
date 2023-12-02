import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.KeyEvent;
import static java.lang.Character.*;
import java.util.*;
import javax.swing.*;
import java.io.IOException;
import java.io.PrintWriter;

public class Space extends Canvas implements MouseMotionListener, MouseWheelListener, MouseListener, KeyListener, Runnable {
	
	private Star star;
	private ArrayList<Planet> ps;
	private ArrayList<Moon> ss;
	private BufferedImage back;
	private double displaySpeed;
	
	private long lastCurrentTime;
	private long simulationTime;
	
	private int mouseDragTempX = -1;
	private int mouseDragTempY = -1;
	
	public double zoom;
	public double viewX;
	public double viewY;
	
	public static double yaw;
	public static double pitch;
	
	public static Frustrum frustrum;
	
	public boolean locked = false;
	public Body lockedBody;
	
	boolean dragging = false;
	
	public static int VIEW_WIDTH;
	public static int VIEW_HEIGHT;
	
	public static int ACTUAL_WIDTH;
	public static int ACTUAL_HEIGHT;
	
	public static final int FRAMERATE = 30;
	
	public Space (int viewWidth, int viewHeight, int actualWidth, int actualHeight, ArrayList<Planet> ps2, Star s2, ArrayList<Moon> ss2) {
		setBackground(Color.BLACK);
		
		lastCurrentTime = System.nanoTime();
		
		ps = ps2;
		star = s2;	
		ss = ss2;
		displaySpeed = 1;
		VIEW_WIDTH = viewWidth;
		VIEW_HEIGHT = viewHeight;
		ACTUAL_WIDTH = actualWidth;
		ACTUAL_HEIGHT = actualHeight;
		zoom = 1;
		viewX = ACTUAL_WIDTH/2;
		viewY = ACTUAL_HEIGHT/2;
		
		yaw = 90;
		pitch = 0;
		
		//camera start location
		//Vector3d position = new Vector3d(star.getX()-936350, star.getY(), 0);
		//camera start angle
		Vector3d l = new Vector3d(1, 0, 0);
		//camera start up
		Vector3d u = new Vector3d(0, 1, 0);

		frustrum = new Frustrum(105, VIEW_WIDTH/VIEW_HEIGHT, 1, ACTUAL_WIDTH*2);
		frustrum.setCameraPosition(star.getX()-934000, star.getY(), 0);
				
		this.addKeyListener(this);
		this.addMouseListener(this);
		this.addMouseWheelListener(this);
		this.addMouseMotionListener(this);
		new Thread(this).start();
	}
	
	public void update (Graphics window) {
		paint(window);
		long currentTime = System.nanoTime();
		long duration = (currentTime - lastCurrentTime);
		lastCurrentTime = currentTime;
		simulationTime += duration * displaySpeed;
		
		try {
			Thread.sleep(1000/FRAMERATE);
			simulationTime += (1000000 * (1000/FRAMERATE)) * displaySpeed;
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	public void paint (Graphics window) {
		
		Graphics2D tdg = (Graphics2D) window;
		
		if(back==null)
		   back = (BufferedImage)(createImage(getWidth(),getHeight()));
		   
		Graphics gtb = back.createGraphics();
		
		gtb.setColor(Color.BLACK);
		gtb.fillRect(0,0,VIEW_WIDTH * 2,VIEW_HEIGHT * 2);
		
		
		//MOVE
		for (Moon s: ss) {
			s.move(simulationTime);
		}
		
		for (Planet p: ps) {
			p.move(simulationTime);
		}
		
		
		//DRAW
		
//		System.out.println("Yaw: " + yaw);
//		System.out.println("Pitch: " + pitch);
		
		//add all bodies to list, sort by distance to camera, draw closest to camera first
		ArrayList<Body> drawList = new ArrayList<Body>();
		
		for (Moon s: ss) {
			drawList.add(s);
		}
		
		for (Planet p: ps) {
			drawList.add(p);
		}
		
		drawList.add(star);
		
//		for (Body b: drawList) {
//			System.out.println(b.getName());
//		}
		
		drawList = sortDrawList(drawList);
		
//		for (Body b: drawList) {
//			System.out.println(b.getName());
//		}
		
		frustrum.cameraYaw = yaw;
		frustrum.cameraPitch = pitch;
		
		for(Body b: drawList) {
			b.draw(gtb, this, frustrum);
		}
		
		tdg.drawImage(back, null, 0, 0);
		
	}
	
	public double[] computeViewMatrix() {
	    double[] viewMatrix = new double[16];

	    // Compute the forward vector based on yaw and pitch
	    double forwardX = (float) (Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
	    double forwardY = (float) (Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
	    double forwardZ = (float) Math.sin(Math.toRadians(pitch));

	    // Compute the right vector by taking the cross product of the forward vector and the up vector (0, 0, 1)
	    double rightX = forwardY;
	    double rightY = -forwardX;
	    double rightZ = 0;

	    // Compute the actual up vector by taking the cross product of the right vector and the forward vector
	    double upX = forwardZ * rightY - forwardY * rightZ;
	    double upY = forwardZ * rightX - forwardX * rightZ;
	    double upZ = forwardX * rightY - forwardY * rightX;

	    double cameraX = frustrum.cameraX;
	    double cameraY = frustrum.cameraY;
	    double cameraZ = frustrum.cameraZ;
	    
	    // Set the view matrix
	    viewMatrix[0] = rightX;
	    viewMatrix[1] = rightY;
	    viewMatrix[2] = rightZ;
	    viewMatrix[3] = 0.0;
	    viewMatrix[4] = upX;
	    viewMatrix[5] = upY;
	    viewMatrix[6] = upZ;
	    viewMatrix[7] = 0.0;
	    viewMatrix[8] = forwardX;
	    viewMatrix[9] = forwardY;
	    viewMatrix[10] = forwardZ;
	    viewMatrix[11] = 0.0;
	    viewMatrix[12] = -rightX * cameraX - rightY * cameraY - rightZ * cameraZ;
	    viewMatrix[13] = -upX * cameraX - upY * cameraY - upZ * cameraZ;
	    viewMatrix[14] = -forwardX * cameraX - forwardY * cameraY - forwardZ * cameraZ;
	    viewMatrix[15] = 1.0;

	    return viewMatrix;
	}
	
	private static ArrayList<Body> sortDrawList(ArrayList<Body> drawList) {
		int n = drawList.size();
        for (int i = 1; i < n; ++i) {
            double key = new Vector3d(drawList.get(i).getX(), drawList.get(i).getY(), 0).distance(new Vector3d(frustrum.cameraX, frustrum.cameraY, frustrum.cameraZ));
            Body keyBody = drawList.get(i);
            int j = i - 1;
 
            /* Move elements of arr[0..i-1], that are
               greater than key, to one position ahead
               of their current position */
            while (j >= 0 && new Vector3d(drawList.get(j).getX(), drawList.get(j).getY(), 0).distance(new Vector3d(frustrum.cameraX, frustrum.cameraY, frustrum.cameraZ)) < key) {
                drawList.set(j+1, drawList.get(j));
                j = j - 1;
            }
            drawList.set(j + 1, keyBody);
        }
        
        return drawList;
	}

	public void run() {
   		try {
	   		while(true) {
	   		   Thread.currentThread().sleep(5);
	           repaint();
	        }
      	}
      	catch(Exception e) {
      	}
  	}
  	
  	public void setDisplaySpeed(double ds) {
  		displaySpeed = ds;
  		for (int i = 0; i < ps.size(); i ++) {
  			ps.get(i).setDisplaySpeed(ds);
  		}
  		for (int i = 0; i < ss.size(); i++) {
  			ss.get(i).setDisplaySpeed(ds);
  		}
  	}
  	
  	public void keyReleased(KeyEvent e) {
		if (e.getKeyCode() == KeyEvent.VK_UP) {
			
		} else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
			
		} else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
			
		} else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
			
		}
	}
	
	public void keyTyped(KeyEvent e) {
		
	}
	
	public void keyPressed(KeyEvent e) {
		if (e.getKeyCode() == KeyEvent.VK_UP) {
			
		}
		if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
			
		}
		if (e.getKeyCode() == KeyEvent.VK_DOWN) {
			
		}
		if (e.getKeyCode() == KeyEvent.VK_LEFT) {
			
		}
		if (e.getKeyCode() == KeyEvent.VK_SPACE) {
			
		}
	}
	
	@Override
	public void mouseWheelMoved(MouseWheelEvent e) {
		 if (e.isControlDown()) {
	     	if (e.getWheelRotation() < 0) {
	        	//System.out.println("mouse wheel Up");
	     		
	        } else {
	        	//System.out.println("mouse wheel Down");
	        	
	        }
		 } else {
			 getParent().dispatchEvent(e);
	     }
	}
	
	@Override
	public void mousePressed(MouseEvent e) {
		Point point = e.getPoint();
		mouseDragTempX = point.x;
		mouseDragTempY = point.y;
		
		dragging = true;
		//System.out.println("dragging: " + dragging);
	}

	@Override
	public void mouseReleased(MouseEvent event) {
		Point point = event.getPoint();
		
		double movementX = (mouseDragTempX - point.x);
		double movementY = (mouseDragTempY - point.y);

		yaw -= Math.toDegrees(movementX/VIEW_WIDTH);
		pitch -= Math.toDegrees(movementY/VIEW_HEIGHT);
		
		mouseDragTempX = -1;
		mouseDragTempY = -1;
		
		dragging = false;
	}
	
	public void mouseDragged(MouseEvent event) {
		if (dragging) {
			Point point = event.getPoint();
			
			double movementX = (mouseDragTempX - point.x);
			double movementY = (mouseDragTempY - point.y);

			yaw -= Math.toDegrees(movementX/VIEW_WIDTH);
			pitch -= Math.toDegrees(movementY/VIEW_HEIGHT);
			
			//System.out.println(movementX + " " + movementY);
			
			mouseDragTempX = point.x;
			mouseDragTempY = point.y;
			
			repaint();
		  }
	 }
	
	public void save(PrintWriter save) {
		//simulation time
		//numplanets
		//planet1
		//planet2
		//nummoons
		//moon1
		//moon2
		save.println(simulationTime);
		save.println(ps.size());
		for(int i = 0; i < ps.size(); i++) {
			save.println(ps.get(i).save());
		}
		save.println(ss.size());
		for(int i = 0; i < ss.size(); i++) {
			save.println(ss.get(i).save());
		}
		
		save.close();
	}
	
	public void load(Scanner load) {
		simulationTime = Long.parseLong(load.next());
		load.nextLine();
		
		int numPlanets = Integer.parseInt(load.next());
		load.nextLine();
		
		for (int i = 0; i < numPlanets; i++) {
			String line = load.nextLine();
			Planet p = new Planet(line, star, displaySpeed);
			ps.add(p);
		}
		
		int numMoons = Integer.parseInt(load.next());
		load.nextLine();
		
		for (int i = 0; i < numMoons; i++) {
			String line = load.nextLine();
			String planetName = line.substring(line.lastIndexOf(" ") + 1);
			
			Planet p = ps.get(0);
			for (int j = 1; j < ps.size(); j++) {
				if (ps.get(j).getName().equals(planetName)) {
					p = ps.get(j);
				}
			}
			
			Moon m = new Moon(line.substring(0, line.lastIndexOf(" ")), p, displaySpeed);
			ss.add(m);
		}
		
		load.close();
		
		lastCurrentTime = System.nanoTime();
	}
	

	@Override
	public void mouseClicked(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseEntered(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseExited(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		
	}


}