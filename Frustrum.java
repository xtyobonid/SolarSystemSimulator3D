import java.awt.geom.Point2D;

public class Frustrum {
    public double fov;
    public double aspectRatio;
    public double near;
    public double far;
    public double cameraX;
    public double cameraY;
    public double cameraZ;
    public double cameraYaw, cameraPitch; 
    private double[] projectionMatrix;
    
    public Frustrum(double fov, double aspectRatio, double near, double far) {
        this.fov = fov;
        this.aspectRatio = aspectRatio;
        this.near = near;
        this.far = far;
        updateProjectionMatrix();
    }

    public void setCameraPosition(double x, double y, double z) {
        this.cameraX = x;
        this.cameraY = y;
        this.cameraZ = z;
    }
    
    public double[] computeViewMatrix() {
        // Compute the rotation matrix based on the yaw and pitch angles
        double cosYaw = (double) Math.cos(cameraYaw);
        double sinYaw = (double) Math.sin(cameraYaw);
        double cosPitch = (double) Math.cos(cameraPitch);
        double sinPitch = (double) Math.sin(cameraPitch);

        double[] rotationMatrix = new double[9];
        rotationMatrix[0] = cosYaw;
        rotationMatrix[1] = -sinYaw * cosPitch;
        rotationMatrix[2] = sinYaw * sinPitch;
        rotationMatrix[3] = 0;
        rotationMatrix[4] = cosPitch;
        rotationMatrix[5] = sinPitch;
        rotationMatrix[6] = -sinYaw;
        rotationMatrix[7] = -cosYaw * cosPitch;
        rotationMatrix[8] = cosYaw * sinPitch;

        // Compute the view matrix
        double[] viewMatrix = new double[16];
        viewMatrix[0] = rotationMatrix[0];
        viewMatrix[1] = rotationMatrix[1];
        viewMatrix[2] = rotationMatrix[2];
        viewMatrix[3] = 0.0;
        viewMatrix[4] = rotationMatrix[3];
        viewMatrix[5] = rotationMatrix[4];
        viewMatrix[6] = rotationMatrix[5];
        viewMatrix[7] = 0.0;
        viewMatrix[8] = rotationMatrix[6];
        viewMatrix[9] = rotationMatrix[7];
        viewMatrix[10] = rotationMatrix[8];
        viewMatrix[11] = 0.0;
        viewMatrix[12] = -(cameraX * viewMatrix[0] + cameraY * viewMatrix[4] + cameraZ * viewMatrix[8]);
        viewMatrix[13] = -(cameraX * viewMatrix[1] + cameraY * viewMatrix[5] + cameraZ * viewMatrix[9]);
        viewMatrix[14] = -(cameraX * viewMatrix[2] + cameraY * viewMatrix[6] + cameraZ * viewMatrix[10]);
        viewMatrix[15] = 1.0;

        return viewMatrix;
    }
    
    public void updateProjectionMatrix() {
        double f = 1.0 / (double) Math.tan(Math.toRadians(fov) / 2.0);
        
        projectionMatrix = new double[16];
        
        projectionMatrix[0] = f / aspectRatio;
        projectionMatrix[1] = 0.0;
        projectionMatrix[2] = 0.0;
        projectionMatrix[3] = 0.0;
        
        projectionMatrix[4] = 0.0;
        projectionMatrix[5] = f;
        projectionMatrix[6] = 0.0;
        projectionMatrix[7] = 0.0;
        
        projectionMatrix[8] = 0.0;
        projectionMatrix[9] = 0.0;
        projectionMatrix[10] = (far + near) / (near - far);
        projectionMatrix[11] = -1.0;
        
        projectionMatrix[12] = 0.0;
        projectionMatrix[13] = 0.0;
        projectionMatrix[14] = (2.0 * far * near) / (near - far);
        projectionMatrix[15] = 0.0;
    }
    
    public double[] worldToCameraSpace(double x, double y, double z, double[] viewMatrix) {
        double[] cameraSpacePosition = new double[4];
        cameraSpacePosition[0] = (float) (viewMatrix[0] * x + viewMatrix[4] * y + viewMatrix[8] * z + viewMatrix[12]);
        cameraSpacePosition[1] = (float) (viewMatrix[1] * x + viewMatrix[5] * y + viewMatrix[9] * z + viewMatrix[13]);
        cameraSpacePosition[2] = (float) (viewMatrix[2] * x + viewMatrix[6] * y + viewMatrix[10] * z + viewMatrix[14]);
        cameraSpacePosition[3] = (float) (viewMatrix[3] * x + viewMatrix[7] * y + viewMatrix[11] * z + viewMatrix[15]);

        return cameraSpacePosition;
    }
    
    public Point2D.Double clipSpaceToScreenSpace(double x, double y, double z, double w, int screenWidth, int screenHeight) {
        if (w > 0) {
            double projectedX = x / w;
            double projectedY = y / w;

            int screenX = (int) ((projectedX * 0.5 + 0.5) * screenWidth);
            int screenY = (int) ((-projectedY * 0.5 + 0.5) * screenHeight);

            return new Point2D.Double(screenX, screenY);
        } else {
            // The point is behind the camera, so don't render it
            return null;
        }
    }
    
    public Point2D.Double project3DTo2D(double x, double y, double z, int screenWidth, int screenHeight) {
        // Multiply the camera space coordinates by the projection matrix
        double[] clipSpacePosition = new double[4];
        clipSpacePosition[0] = projectionMatrix[0] * x + projectionMatrix[4] * y + projectionMatrix[8] * z + projectionMatrix[12];
        clipSpacePosition[1] = projectionMatrix[1] * x + projectionMatrix[5] * y + projectionMatrix[9] * z + projectionMatrix[13];
        clipSpacePosition[2] = projectionMatrix[2] * x + projectionMatrix[6] * y + projectionMatrix[10] * z + projectionMatrix[14];
        clipSpacePosition[3] = projectionMatrix[3] * x + projectionMatrix[7] * y + projectionMatrix[11] * z + projectionMatrix[15];

        // Convert the clip space coordinates to screen space
        return clipSpaceToScreenSpace(clipSpacePosition[0], clipSpacePosition[1], clipSpacePosition[2], clipSpacePosition[3], screenWidth, screenHeight);
    }
}