import javax.swing.JFrame;
import java.awt.*;
import java.util.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.awt.event.KeyEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JTextField;
import javax.swing.JComboBox;
import javax.swing.JSlider;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.AbstractButton;
import javax.swing.JOptionPane;

//To do:
// ellipse orbits
// fix lock onto moon
//   !!! maybe issue with drawing is system time passes between the lock, move, and the draw
//      perhaps solution is to save system time for the draw

public class PSP extends JFrame implements ActionListener, ChangeListener {
	
	private static java.awt.Dimension scr = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
	public static final int VIEW_WIDTH = scr.width;
	public static final int VIEW_HEIGHT = scr.height;
	public static final int ACTUAL_WIDTH = 80000000;
	public static final int ACTUAL_HEIGHT = 80000000;
	public static final double SUN_RADIUS = 6963.4;
	public static final int DISPLAY_MODIFIER = 23;
	
	private static JButton addPlanet;
	private static JButton addMoon;
	private static JSlider displaySpeedSlider;
	private static JButton removePlanet;
	private static JButton removeMoon;
	private static JButton showList;
	private static JButton saveSystem;
	private static JButton loadSystem;
	private static JTextField lockField;
	private static JButton lockButton; 
	private static JButton unlockButton;
	
	private static JTextField removePlanetName;
	private static JButton removePlanet2;
	
	private static JTextField removeMoonName;
	private static JTextField removeMoonPName;
	private static JButton removeMoon2;
	
	private static JLabel list;
	
	private static JTextField pRadius;
	private static JTextField pOrbitDistance;
	private static JTextField pOrbitSpeed;
	private static JTextField pStartAngle;
	private static JTextField pName;
	private static JComboBox pColor;
	private static JButton pColorChooser;
	private static JButton finishPlanet;
	private static JLabel pRadiusL;
	private static JLabel pStartAngleL;
	private static JLabel pOrbitDistanceL;
	private static JLabel pOrbitSpeedL;
	private static JLabel pColorL;
	
	private static JTextField mRadius;
	private static JTextField mOrbitDistance;
	private static JTextField mOrbitSpeed;
	private static JTextField mName;
	private static JTextField getPName;
	private static JComboBox mColor;
	private static JButton mColorChooser;
	private static JButton finishMoon;
	private static JLabel mRadiusL;
	private static JLabel mOrbitDistanceL;
	private static JLabel mOrbitSpeedL;
	private static JLabel mColorL;
	
	private static ArrayList<Planet> planets;
	private static ArrayList<Moon> moons;
	private static Star star;
	
	private boolean colorPicked = false;
	private Color tempColorPicked;
	
	private int displaySpeed = 1;
	
	private Space tg;
	
	@SuppressWarnings("unchecked")
	public PSP () {
		super("PSP");
		setSize(VIEW_WIDTH, VIEW_HEIGHT);
		setLocation(0,0);
		
		planets = new ArrayList<Planet>();
		moons = new ArrayList<Moon>();
		star = new Star(ACTUAL_WIDTH, ACTUAL_HEIGHT, SUN_RADIUS);
		
		tg = new Space(VIEW_WIDTH, VIEW_HEIGHT, ACTUAL_WIDTH, ACTUAL_HEIGHT, planets, star, moons);
		((Component)tg).setFocusable(true);
		
		getContentPane().add(tg);
		
		addPlanet = new JButton("Add New Planet");
		addPlanet.setActionCommand("newPlanet");
		addPlanet.addActionListener(this);
		
		addMoon = new JButton("Add New Moon");
		addMoon.setActionCommand("newMoon");
		addMoon.addActionListener(this);
		
		displaySpeedSlider = new JSlider(1, DISPLAY_MODIFIER);
		displaySpeedSlider.setMajorTickSpacing(DISPLAY_MODIFIER/5);
		displaySpeedSlider.setMinorTickSpacing(DISPLAY_MODIFIER/25);
		displaySpeedSlider.setPaintTicks(true);
		displaySpeedSlider.setValue(1);
		displaySpeedSlider.addChangeListener(this);
		
		removePlanet = new JButton("Remove Planet");
		removePlanet.setActionCommand("removePlanet");
		removePlanet.addActionListener(this);
		
		removeMoon = new JButton("Remove Moon");
		removeMoon.setActionCommand("removeMoon");
		removeMoon.addActionListener(this);
		
		showList = new JButton("List of Celestial Bodies");
		showList.setActionCommand("showList");
		showList.addActionListener(this);
		
		saveSystem = new JButton("Save System");
		saveSystem.setActionCommand("save");
		saveSystem.addActionListener(this);
		
		loadSystem = new JButton("Load System");
		loadSystem.setActionCommand("load");
		loadSystem.addActionListener(this);
		
		lockField = new JTextField("Body to lock onto", 15);
		
		lockButton = new JButton("Lock");
		lockButton.setActionCommand("lock");
		lockButton.addActionListener(this);
		
		unlockButton = new JButton("Unlock");
		unlockButton.setActionCommand("unlock");
		unlockButton.addActionListener(this);
		
		removePlanetName = new JTextField("Name", 25);
		
		removePlanet2 = new JButton("Remove Planet");
		removePlanet2.setActionCommand("removePlanet2");
		removePlanet2.addActionListener(this);
		
		
		
		removeMoonName = new JTextField("Name", 25);
		
		removeMoonPName = new JTextField("Planet Name", 25);
		
		removeMoon2 = new JButton("Remove Moon");
		removeMoon2.setActionCommand("removeMoon2");
		removeMoon2.addActionListener(this);
		
		
		
		list = new JLabel();
		
		
		
		finishPlanet = new JButton("Finish Planet");
		finishPlanet.setActionCommand("finishPlanet");
		finishPlanet.addActionListener(this);
		
		pRadius = new JTextField("Planet Radius", 25);
		
		pOrbitDistance = new JTextField("Planet Orbit Radius", 25);
		
		pOrbitSpeed = new JTextField("Planet Orbit Speed", 25);
		
		pStartAngle = new JTextField("Planet Start Angle", 25);
		
		pStartAngleL = new JLabel("Start Angle of Planet");
		
		pRadiusL = new JLabel("Radius of Planet");
		pOrbitDistanceL = new JLabel("Distance of Orbit");
		pOrbitSpeedL = new JLabel("Speed of Orbit");
		pColorL = new JLabel("Planet Color");
		
		pName = new JTextField("Name", 25);
		
		String[] colorsArray = {"Blue", "Cyan", "Dark Gray", "Gray", "Green", "Light Gray", "Magenta", "Orange", "Pink", "Red", "White", "Yellow", "Black"};
		pColor = new JComboBox(colorsArray);
		
		pColorChooser = new JButton("Color Chooser");
		pColorChooser.setActionCommand("pChooseColor");
		pColorChooser.addActionListener(this);
		
		mRadius = new JTextField("Moon Radius", 25);
		
		mOrbitDistance = new JTextField("Moon Orbit Radius", 25);
		
		mOrbitSpeed = new JTextField("Moon Orbit Speed", 25);
		
		mName = new JTextField("Name", 25);
		
		mColorChooser = new JButton("Color Chooser");
		mColorChooser.setActionCommand("mChooseColor");
		mColorChooser.addActionListener(this);
		
		getPName = new JTextField("Planet's Name", 25);
		
		mColor = new JComboBox(colorsArray);
		
		finishMoon = new JButton("Finish Moon");
		finishMoon.setActionCommand("finishMoon");
		finishMoon.addActionListener(this);
		
		mRadiusL = new JLabel("Radius of Moon");
		mOrbitDistanceL = new JLabel("Distance of Orbit");
		mOrbitSpeedL = new JLabel("Speed of Orbit");
		mColorL = new JLabel("Moon Color");
		
		
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setVisible(true);
	}
	
	public static void main(String[] args) {
		
		PSP go = new PSP();
		
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                createMainGUI(); 
            }
        });
	}
	
	public void actionPerformed(ActionEvent e) {
	 	if ("newPlanet".equals(e.getActionCommand())) {
	 		javax.swing.SwingUtilities.invokeLater(new Runnable() {
	            public void run() {
	                createPlanetGUI(); 
	            }
        	});
        } else if ("finishPlanet".equals(e.getActionCommand())) {
        	double pRadiusN = Double.parseDouble(pRadius.getText());
        	double orbitD = Double.parseDouble(pOrbitDistance.getText()) + star.getRadius();
        	double orbitS = Double.parseDouble(pOrbitSpeed.getText());
        	double startAngle = Double.parseDouble(pStartAngle.getText());
        	String pNameS = pName.getText();
        	boolean finished = true;
        	for (int i = 0; i < planets.size(); i++) {
        		if (planets.get(i).getName().equals(pNameS)) {
        			finished = false;
        		}
        	}
        	if (!finished) {
        		JOptionPane.showMessageDialog(null, "Planet already exists, enter a valid planet's name");
        	} else {
        		if (!colorPicked) {
        			int ind = pColor.getSelectedIndex();
        			Color[] cs = {Color.BLUE, Color.CYAN, Color.DARK_GRAY, Color.GRAY, Color.GREEN, Color.LIGHT_GRAY, Color.MAGENTA, Color.ORANGE, Color.PINK, Color.RED, Color.WHITE, Color.YELLOW, Color.BLACK};
//        			planets.add(new Planet(pNameS, pRadiusN, startAngle, orbitD, star, orbitS, cs[ind]));
        		} else {
//        			planets.add(new Planet(pNameS, pRadiusN, startAngle, orbitD, star, orbitS, tempColorPicked));
        			colorPicked = false;
        		}
	        	
	        	
	        	stateChanged(new ChangeEvent(displaySpeedSlider));
        	}
        } else if ("newMoon".equals(e.getActionCommand())) {
        	javax.swing.SwingUtilities.invokeLater(new Runnable() {
	            public void run() {
	                createMoonGUI(); 
	            }
        	});
        } else if ("finishMoon".equals(e.getActionCommand())) {
        	double mRadiusN = Double.parseDouble(mRadius.getText());
        	double orbitS = Double.parseDouble(mOrbitSpeed.getText());
        	String mNameS = mName.getText();
        	String pNameS = getPName.getText();
        	Planet p = new Planet(star);
        	boolean finished = false;
        	for (int i = 0; i < planets.size(); i++) {
	       		if (planets.get(i).getName().equals(pNameS)) {
	      			p = planets.get(i);
	       			finished = true;
	        		break;
	        	}
	        }
	        if (!finished) {
	        	JOptionPane.showMessageDialog(null, "Planet does not exist, enter a valid Planet name");
	        } else {
	        	finished = true;
	        	for (int i = 0; i < moons.size(); i++) {
	        		if (moons.get(i).getPlanetName().equals(p.getName()) && moons.get(i).getName().equals(mNameS)) {
	        			finished = false;
	        		}
	        	}
	        	if (!finished) {
	        		JOptionPane.showMessageDialog(null, "Moon already exists, enter a valid Moon name");
	        	} else {
		        	double orbitD = Double.parseDouble(mOrbitDistance.getText()) + p.getRadius();
		        	
		        	if (!colorPicked) {
			        	int ind = mColor.getSelectedIndex();
			        	Color[] cs = {Color.BLUE, Color.CYAN, Color.DARK_GRAY, Color.GRAY, Color.GREEN, Color.LIGHT_GRAY, Color.MAGENTA, Color.ORANGE, Color.PINK, Color.RED, Color.WHITE, Color.YELLOW, Color.BLACK};
//			        	moons.add(new Moon(mRadiusN, orbitD, p, mNameS, cs[ind], orbitS));
		        	} else {
//		        		moons.add(new Moon(mRadiusN, orbitD, p, mNameS, tempColorPicked, orbitS));
	        			colorPicked = false;
	        		}
		        	stateChanged(new ChangeEvent(displaySpeedSlider));
	        	}
	        }
        } else if ("removePlanet2".equals(e.getActionCommand())) {
        	String name = removePlanetName.getText();
        	int index = -1;
        	for (int i = 0; i < planets.size(); i ++) {
        		if (planets.get(i).getName().equals(name))
        			index = i;
        	}
        	if (index == -1) {
        		JOptionPane.showMessageDialog(null, "Planet does not exist, enter a valid Planet name");
        	} else {
        		for (int i = 0; i < moons.size(); i++) {
        			if (moons.get(i).getPlanetName().equals(planets.get(index).getName())) {
        				moons.remove(i);
        				i--;
        			}
        		}
        		planets.remove(index);
        	}
     	} else if ("removePlanet".equals(e.getActionCommand())) {
     		javax.swing.SwingUtilities.invokeLater(new Runnable() {
	            public void run() {
	                removePlanetGUI(); 
	            }
        	});
     	} else if ("removeMoon".equals(e.getActionCommand())) {
     		javax.swing.SwingUtilities.invokeLater(new Runnable() {
	            public void run() {
	                removeMoonGUI(); 
	            }
        	});
     	} else if ("removeMoon2".equals(e.getActionCommand())) {
     		String name = removeMoonName.getText();
     		String nameP = removeMoonPName.getText();
     		int index = -1;
     		for (int i = 0; i < moons.size(); i++) {
     			if (moons.get(i).getName().equals(name) && moons.get(i).getPlanetName().equals(nameP))
     				index = i;
     		}
     		if (index == -1) {
     			JOptionPane.showMessageDialog(null, "Moon does not exist, enter a valid Moon name");
     		} else {
     			moons.remove(index);
     		}
     	} else if ("showList".equals(e.getActionCommand())) {
     		javax.swing.SwingUtilities.invokeLater(new Runnable() {
	            public void run() {
	                showListGUI(); 
	            }
        	});
     	} else if ("pChooseColor".equals(e.getActionCommand())) {
     		colorPicked = true;
     		tempColorPicked = JColorChooser.showDialog(this, "Colors", Color.BLACK);
     	} else if ("save".equals(e.getActionCommand())) {
     		try {
				PrintWriter save = new PrintWriter(new BufferedWriter(new FileWriter("systemSave.txt")));
				tg.save(save);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
     	} else if ("load".equals(e.getActionCommand())) {
     		try {
				Scanner load = new Scanner(new File("solar_system_elliptical.save"));
				tg.load(load);
			} catch (FileNotFoundException e1) {
				e1.printStackTrace();
			}
     	} else if ("lock".equals(e.getActionCommand())) {
     		Body lockedBody = star;
     		String bodyName = lockField.getText();
     		
			for (int i = 0; i < planets.size(); i++) {
				if (planets.get(i).getName().equals(bodyName)) {
					lockedBody = planets.get(i);
				}
			}
			
			for (int i = 0; i < moons.size(); i++) {
				if (moons.get(i).getName().equals(bodyName)) {
					lockedBody = moons.get(i);
				}
			}
			
			tg.lockToBody(lockedBody);
     	} else if ("unlock".equals(e.getActionCommand())) {
     		tg.unlockBody();
     	} else if ("mChooseColor".equals(e.getActionCommand())) {
     		colorPicked = true;
     		tempColorPicked = JColorChooser.showDialog(this, "Colors", Color.BLACK);
     	}
	}
	
	public void stateChanged(ChangeEvent e) {
		int gv = displaySpeedSlider.getValue();
		int gvd = (int) java.lang.Math.pow(2, gv - 1);
		tg.setDisplaySpeed(gvd);
	}
	
	public static void createMainGUI() {
		JFrame frame = new JFrame("Control Bar");
		frame.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(261, 850);
		frame.setLocation(0,0);
		
		
		c.insets = new Insets(10,30,10,30);
		c.gridx = 0;
		c.gridy = 0;
		c.gridwidth = 2;
		frame.add(addPlanet, c);
		
		c.insets = new Insets(10,30,10,30);
		c.gridx = 0;
		c.gridy = 1;
		c.gridwidth = 2;
		frame.add(addMoon, c);
		
		c.insets = new Insets(10,30,10,30);
		c.gridx = 0;
		c.gridy = 2;
		c.gridwidth = 2;
		frame.add(removePlanet, c);
		
		c.insets = new Insets(10,30,10,30);
		c.gridx = 0;
		c.gridy = 3;
		c.gridwidth = 2;
		frame.add(removeMoon, c);
		
		c.insets = new Insets(10,30,10,30);
		c.gridx = 0;
		c.gridy = 4;
		c.gridwidth = 2;
		frame.add(showList, c);
		
		c.insets = new Insets(10,30,0,30);
		c.gridx = 0;
		c.gridy = 5;
		c.gridwidth = 1;
		frame.add(lockField, c);
		
		c.insets = new Insets(0,30,10,30);
		c.gridx = 1;
		c.gridy = 5;
		c.gridwidth = 1;
		frame.add(lockButton, c);
		
		c.insets = new Insets(10,30,10,30);
		c.gridx = 0;
		c.gridy = 6;
		c.gridwidth = 2;
		frame.add(unlockButton, c);
		
		c.insets = new Insets(10,30,10,30);
		c.gridx = 0;
		c.gridy = 7;
		c.gridwidth = 2;
		frame.add(saveSystem, c);
		
		c.insets = new Insets(10,30,10,30);
		c.gridx = 0;
		c.gridy = 8;
		c.gridwidth = 2;
		frame.add(loadSystem, c);
				
		c.insets = new Insets(10,30,10,30);
		c.gridx = 0;
		c.gridy = 9;
		c.gridwidth = 2;
		frame.add(displaySpeedSlider, c);
		
		frame.pack();
		frame.setVisible(true);		
	}
	
	public static void showListGUI() {
		JFrame frame = new JFrame("List of Celestial Bodies");
		frame.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		
		c.insets = new Insets(10,30,10,30);
		c.gridx = 0;
		c.gridy = 0;
		
		String list2 = "<html>";
		for (int i = 0; i < planets.size(); i++) {
			list2 += planets.get(i).getName() + "<br>";
			ArrayList<String> moonNames = getPlanetMoons(planets.get(i).getName());
			for (int j = 0; j < moonNames.size(); j++) {
				list2 += ">   " + moonNames.get(j) + "<br>";
			}
		}
		list2 += "</html>";
		list.setText(list2);
		frame.add(list, c);
		
		frame.pack();
		frame.setVisible(true);
	}
	
	public static ArrayList<String> getPlanetMoons(String planet) {
		ArrayList<String> moonNames = new ArrayList<String>();
		for(int i = 0; i < moons.size(); i++)
			if (moons.get(i).getPlanetName().equals(planet))
				moonNames.add(moons.get(i).getName());
		return moonNames;
	}
	
	public static void removePlanetGUI() {
		JFrame frame = new JFrame("Remove Planet");
		frame.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		
		c.insets = new Insets(10,30,10,30);
		c.gridx = 0;
		c.gridy = 0;
		frame.add(removePlanetName, c);
		
		c.insets = new Insets(10,30,10,30);
		c.gridx = 0;
		c.gridy = 1;
		frame.add(removePlanet2, c);
		
		frame.pack();
		frame.setVisible(true);
	}
	
	public static void removeMoonGUI() {
		JFrame frame = new JFrame("Remove Moon");
		frame.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		
		c.insets = new Insets(10,30,10,30);
		c.gridx = 0;
		c.gridy = 0;
		frame.add(removeMoonName, c);
		
		c.insets = new Insets(10,30,10,30);
		c.gridx = 0;
		c.gridy = 1;
		frame.add(removeMoonPName, c);
		
		c.insets = new Insets(10,30,10,30);
		c.gridx = 0;
		c.gridy = 2;
		frame.add(removeMoon2, c);
		
		frame.pack();
		frame.setVisible(true);
	}
	
	public static void createMoonGUI() {
		JFrame frame = new JFrame("Create New Moon");
		frame.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		
		c.insets = new Insets(10,0,0,10);
		c.gridx = 1;
		c.gridy = 0;
		frame.add(mRadius, c);
		c.insets = new Insets(0,10,0,0);
		c.gridx = 0;
		c.gridy = 0;
		frame.add(mRadiusL, c);
		
		c.insets = new Insets(10,0,0,10);
		c.gridx = 1;
		c.gridy = 1;
		frame.add(mOrbitDistance, c);
		c.insets = new Insets(0,10,0,0);
		c.gridx = 0;
		c.gridy = 1;
		frame.add(mOrbitDistanceL, c);
		
		c.insets = new Insets(10,0,0,10);
		c.gridx = 1;
		c.gridy = 2;
		frame.add(mOrbitSpeed, c);
		c.insets = new Insets(0,10,0,0);
		c.gridx = 0;
		c.gridy = 2;
		frame.add(mOrbitSpeedL, c);
		
		c.insets = new Insets(10,0,0,10);
		c.gridx = 1;
		c.gridy = 3;
		frame.add(mName, c);
		
		c.insets = new Insets(10,0,0,10);
		c.gridx = 1;
		c.gridy = 4;
		frame.add(getPName, c);
		
		c.insets = new Insets(10,0,0,10);
		c.gridx = 1;
		c.gridy = 5;
		frame.add(mColor, c);
		c.insets = new Insets(0,10,0,0);
		c.gridx = 0;
		c.gridy = 5;
		frame.add(mColorL, c);
		
		c.insets = new Insets(10,10,10,10);
		c.gridx = 1;
		c.gridy = 6;
		frame.add(mColorChooser, c);
		
		c.insets = new Insets(10,0,10,10);
		c.gridx = 1;
		c.gridy = 7;
		frame.add(finishMoon, c);
		
		frame.pack();
		frame.setVisible(true);
	}
	
	public static void createPlanetGUI() {
		JFrame frame = new JFrame("Create New Planet");
		frame.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		
		c.insets = new Insets(10,0,0,10);
		c.gridx = 1;
		c.gridy = 0;
		frame.add(pRadius, c);
		c.insets = new Insets(0,10,0,0);
		c.gridx = 0;
		c.gridy = 0;
		frame.add(pRadiusL, c);
		
		c.insets = new Insets(10,0,0,10);
		c.gridx = 1;
		c.gridy = 1;
		frame.add(pOrbitDistance, c);
		c.insets = new Insets(0,10,0,0);
		c.gridx = 0;
		c.gridy = 1;
		frame.add(pOrbitDistanceL, c);
		
		c.insets = new Insets(10,0,0,10);
		c.gridx = 1;
		c.gridy = 2;
		frame.add(pOrbitSpeed, c);
		c.insets = new Insets(0,10,0,0);
		c.gridx = 0;
		c.gridy = 2;
		frame.add(pOrbitSpeedL, c);
		
		c.insets = new Insets(10,0,0,10);
		c.gridx = 1;
		c.gridy = 3;
		frame.add(pName, c);
		
		c.insets = new Insets(10,0,0,10);
		c.gridx = 1;
		c.gridy = 4;
		frame.add(pColor, c);
		c.insets = new Insets(0,10,0,0);
		c.gridx = 0;
		c.gridy = 4;
		frame.add(pColorL, c);
		
		c.insets = new Insets(10,0,0,10);
		c.gridx = 1;
		c.gridy = 5;
		frame.add(pStartAngle, c);
		c.insets = new Insets(0,10,0,0);
		c.gridx = 0;
		c.gridy = 5;
		frame.add(pStartAngleL, c);
		
		c.insets = new Insets(10,10,10,10);
		c.gridx = 1;
		c.gridy = 6;
		frame.add(pColorChooser, c);
		
		c.insets = new Insets(10,0,10,10);
		c.gridx = 1;
		c.gridy = 7;
		frame.add(finishPlanet, c);
		
		frame.pack();
		frame.setVisible(true);
	}
}