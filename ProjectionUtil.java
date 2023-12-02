import java.awt.Point;
import java.awt.geom.Point2D;

public class ProjectionUtil {
//	public static Point project3DTo2D(double x, double y, double z, double[] viewMatrix, Frustrum frustrum, int screenWidth, int screenHeight) {
//        // Transform the 3D point using the view matrix
//        double[] worldCoordinates = new double[]{x, y, z, 1.0f};
//        double[] viewCoordinates = new double[4];
//
//        for (int i = 0; i < 4; i++) {
//            viewCoordinates[i] = 0.0f;
//            for (int j = 0; j < 4; j++) {
//                viewCoordinates[i] += viewMatrix[i * 4 + j] * worldCoordinates[j];
//            }
//        }
//
//        // Apply perspective projection
//        double perspectiveFactor = -frustrum.near / viewCoordinates[2];
//        double projectedX = viewCoordinates[0] * perspectiveFactor;
//        double projectedY = viewCoordinates[1] * perspectiveFactor;
//
//        // Transform the projected coordinates to screen coordinates
//        int screenX = (int) ((projectedX / (frustrum.near * (float) Math.tan(Math.toRadians(frustrum.fov / 2.0)))) * (screenWidth / 2) + (screenWidth / 2));
//        int screenY = (int) ((projectedY / (frustrum.near * (float) Math.tan(Math.toRadians(frustrum.fov / 2.0)))) * (screenHeight / 2) + (screenHeight / 2));
//
//        return new Point(screenX, screenY);
//    }
	
//	public Point2D.Double project3DTo2D(float x, float y, float z, screenWidth) {
//	    // Perform the perspective projection
//	    double projectedX = projectionMatrix[0] * x + projectionMatrix[4] * y + projectionMatrix[8] * z + projectionMatrix[12];
//	    double projectedY = projectionMatrix[1] * x + projectionMatrix[5] * y + projectionMatrix[9] * z + projectionMatrix[13];
//	    double projectedZ = projectionMatrix[2] * x + projectionMatrix[6] * y + projectionMatrix[10] * z + projectionMatrix[14];
//	    double projectedW = projectionMatrix[3] * x + projectionMatrix[7] * y + projectionMatrix[11] * z + projectionMatrix[15];
//
//	    // Perform the perspective divide
//	    if (projectedW > 0) {
//	        projectedX /= projectedW;
//	        projectedY /= projectedW;
//	        projectedZ /= projectedW;
//
//	        // Convert the normalized device coordinates to screen coordinates
//	        int screenX = (int) ((projectedX * 0.5 + 0.5) * screenWidth);
//	        int screenY = (int) ((-projectedY * 0.5 + 0.5) * screenHeight);
//
//	        return new Point2D.Double(screenX, screenY);
//	    } else {
//	        // The point is behind the camera, so don't render it
//	        return null;
//	    }
//	}
}