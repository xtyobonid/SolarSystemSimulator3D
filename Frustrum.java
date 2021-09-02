

public class Frustrum {
	public static final int TOP = 0;
	public static final int BOTTOM = 1;
	public static final int LEFT = 2;
	public static final int RIGHT = 3;
	public static final int NEARP = 4;
	public static final int FARP = 5;
	
	public static final double ANG2RAD = 3.14159265358979323846/180.0;
	
	Plane[] pl = new Plane[6];
	Vector3d ntl, ntr, nbl, nbr, ftl, ftr, fbl, fbr;
	double nearD, farD, ratio, angle, tang;
	double nw, nh, fw, fh;
	
	public Vector3d p;
	public Vector3d l;
	public Vector3d u;
	
	public void setCamInternals(double angle, double ratio, double nearD, double farD, Vector3d p, Vector3d l, Vector3d u) {
		// store the information
		this.ratio = ratio;
		this.angle = angle;
		this.nearD = nearD;
		this.farD = farD;
		this.p = p;
		this.l = l;
		this.u = u;

		// compute width and height of the near and far plane sections
		tang = (double)Math.tan(ANG2RAD * angle * 0.5) ;
		nh = nearD * tang;
		nw = nh * ratio;
		fh = farD  * tang;
		fw = fh * ratio;
	}
	
	public void setCamDef() {
		Vector3d dir,nc,fc,X,Y,Z;

		//l = z
		//u = y
		//right = l x u = x
		Vector3d right = l.cross(u);
		// compute the centers of the near and far planes
		nc = p.add(l.mul(nearD));
		fc = p.add(l.mul(farD));

		// compute the 4 corners of the frustrum on the near plane
		ntl = nc.add(u.mul(nh)).sub(right.mul(nw));
		ntr = nc.add(u.mul(nh)).add(right.mul(nw));
		nbl = nc.sub(u.mul(nh)).sub(right.mul(nw));
		nbr = nc.sub(u.mul(nh)).add(right.mul(nw));

		// compute the 4 corners of the frustrum on the far plane
		ftl = fc.add(u.mul(fh)).sub(right.mul(fw));
		ftr = fc.add(u.mul(fh)).add(right.mul(fw));
		fbl = fc.sub(u.mul(fh)).sub(right.mul(fw));
		fbr = fc.sub(u.mul(fh)).add(right.mul(fw));

		// compute the six planes
		// the function set3Points assumes that the points
		// are given in counter clockwise order
		for (int i = 0; i < pl.length; i++) {
			pl[i] = new Plane();
		}
		
		pl[TOP].set3Points(ntr,ntl,ftl);
		pl[BOTTOM].set3Points(nbl,nbr,fbr);
		pl[LEFT].set3Points(ntl,nbl,fbl);
		pl[RIGHT].set3Points(nbr,ntr,fbr);
		pl[NEARP].set3Points(ntl,ntr,nbr);
		pl[FARP].set3Points(ftr,ftl,fbl);
	}
	
	public boolean sphereInFrustum(Vector3d p, double radius) {

		double distance;
		boolean result = true;
		
		// loop max value of 6 checks far plane, 5 doesn't for infinite view distance
		for(int i = 0; i < 5; i++) {
			distance = pl[i].distance(p);
			if (distance < 0)
				return false;
		}
		return(result);
	}

	//http://www.ambrsoft.com/TrigoCalc/Plan3D/Plane3D_.htm
	//http://www.ambrsoft.com/TrigoCalc/Line3D/Line3D_.htm
	//http://www.ambrsoft.com/TrigoCalc/Plan3D/PlaneLineIntersection_.htm
	public Vector3d getIntersectionWithViewPlane(Vector3d point, int VIEW_WIDTH, double radius) {
		
		//plane equation
		double x1 = pl[NEARP].a.x;
		double y1 = pl[NEARP].a.y;
		double z1 = pl[NEARP].a.z; 
		
		double x2 = pl[NEARP].b.x;
		double y2 = pl[NEARP].b.y;
		double z2 = pl[NEARP].b.z; 
		
		double x3 = pl[NEARP].c.x;
		double y3 = pl[NEARP].c.y;
		double z3 = pl[NEARP].c.z; 
		
		double planeA = (y2 * z3) - (y3 * z2) - (y1 * (z3 - z2)) + (z1 * (y3 - y2));
		double planeB = (x1 * (z3 - z2)) - ((x2 * z3) - (x3 * z2)) + (z1 * (x2 - x3));
		double planeC = (x1 * (y2 - y3)) - (y1 * (x2 - x3)) + ((x2 * y3) - (x3 * y2));
		double planeD = -(x1 * ((y2 * z3) - (y3 * z2))) + (y1 * ((x2 * z3) - (x3 * z2))) - (z1 * ((x2 * y3) - (x3 * y2)));
		
		double lineX = p.x;
		double lineY = p.y;
		double lineZ = p.z;
		double lineA = point.x - p.x;
		double lineB = point.y - p.y;
		double lineC = point.z - p.z;
		
		double top = -((planeA * lineX) + (planeB * lineY) + (planeC * lineZ) + planeD);
		double bottom = (planeA * lineA) + (planeB * lineB) + (planeC * lineC);
		double t = top/bottom;
		
		double intersectX = lineX + (lineA * t);
		double intersectY = lineY + (lineB * t);
		double intersectZ = lineZ + (lineC * t);
		Vector3d intersect = new Vector3d(intersectX, intersectY, intersectZ);
		
		//get distance of near plane intersection to 2 of the near plane corners, top left and top right
		double distanceToTopLeft = intersect.distance(pl[NEARP].a);
		double distanceToTopRight = intersect.distance(pl[NEARP].b);
		
		//convert to screen coords
		//http://www.ambrsoft.com/TrigoCalc/Circles2/circle2intersection/CircleCircleIntersection.htm
		double tlCircleA = 0;
		double tlCircleB = 0;
		double tlCircleR = ((distanceToTopLeft/nw) * VIEW_WIDTH)/2;
		
		double trCircleA = 0;
		double trCircleB = VIEW_WIDTH;
		double trCircleR = ((distanceToTopRight/nw) * VIEW_WIDTH)/2;
		
		double calcD = java.lang.Math.sqrt(java.lang.Math.pow(trCircleA - tlCircleA, 2) + java.lang.Math.pow(trCircleB - tlCircleB, 2));
		double calcDelta = 0.25 * java.lang.Math.sqrt(java.lang.Math.abs((calcD + tlCircleR + trCircleR) * (calcD + tlCircleR - trCircleR) * (calcD - tlCircleR + trCircleR) * (-calcD + tlCircleR + trCircleR)));
		
		double screenX = java.lang.Math.abs(((tlCircleA + trCircleA)/2) + (((trCircleA - tlCircleA) * (java.lang.Math.pow(tlCircleR, 2) - java.lang.Math.pow(trCircleR, 2))) / (2 * java.lang.Math.pow(calcD, 2))) + (2 * ((tlCircleB + trCircleB)/(java.lang.Math.pow(calcD, 2))) * calcDelta));;
		double screenY = java.lang.Math.abs(((tlCircleB + trCircleB)/2) + (((trCircleB - tlCircleB) * (java.lang.Math.pow(tlCircleR, 2) - java.lang.Math.pow(trCircleR, 2))) / (2 * java.lang.Math.pow(calcD, 2))) + (2 * ((tlCircleA + trCircleA)/(java.lang.Math.pow(calcD, 2))) * calcDelta));
		
		
		
		//http://neoprogrammics.com/sphere_angular_diameter/
		double distance = p.distance(point);
		double screenR = 2 * java.lang.Math.acos((java.lang.Math.sqrt(java.lang.Math.pow(distance, 2) - java.lang.Math.pow(radius, 2)))/(distance));
		if (distance > radius) {
			screenR = (screenR * 180) / java.lang.Math.PI;
		} else {
			screenR = angle*2;
		}
		screenR = (screenR/angle * VIEW_WIDTH)/2;
		
		//System.out.println(screenR);
		
		//return 
		Vector3d ret = new Vector3d(screenX, screenY, screenR);
		return ret;
	}
	
}
