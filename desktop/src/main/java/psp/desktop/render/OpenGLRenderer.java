package psp.desktop.render;

import org.joml.Matrix4f;
import psp.desktop.app.SimulationScene;
import psp.desktop.app.StarfieldData;
import psp.desktop.app.RenderBody;

import java.nio.FloatBuffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.BufferUtils;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL32.GL_PROGRAM_POINT_SIZE;

public final class OpenGLRenderer implements Renderer {

    // Starfield GL objects
    private boolean initialized = false;
    private int starVao = 0;
    private int starVbo = 0;
    private ShaderProgram starShader;
    private int uProjLoc = -1;
    private int uViewRotLoc = -1;
    private int uStarDistanceLoc = -1;

    // Body impostor GL objects
    private int bodyVao = 0;
    private int bodyVbo = 0;
    private ShaderProgram bodyShader;
    private int uBodyProjLoc = -1;
    private int uBodyViewRotLoc = -1;
    private int uBodyViewportHLoc = -1;
    private int uBodySunViewPosLoc = -1;

    // CPU-side staging buffer for body instance data (reused)
    private FloatBuffer bodyInstanceBuffer = BufferUtils.createFloatBuffer(1024 * 8); // grows if needed


    @Override
    public void render(SimulationScene scene) {
        if (!initialized) init(scene);

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);

        glClearColor(0f, 0f, 0f, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // Starfield: don't write depth (so it never blocks planets)
        glDepthMask(false);
        renderStarfield(scene);
        glDepthMask(true);

        renderBodies(scene);
    }

    private void init(SimulationScene scene) {
        // --- Create VAO/VBO for stars ---
        StarfieldData data = scene.starfield();

        starVao = glGenVertexArrays();
        glBindVertexArray(starVao);

        starVbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, starVbo);
        glBufferData(GL_ARRAY_BUFFER, data.stars, GL_STATIC_DRAW);

        // layout(location=0) vec4 aStar;
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 4, GL_FLOAT, false, 4 * Float.BYTES, 0);

        glBindVertexArray(0);

        // --- Shaders ---
        starShader = new ShaderProgram(STAR_VS, STAR_FS);
        uProjLoc = starShader.uniformLocation("uProj");
        uViewRotLoc = starShader.uniformLocation("uViewRot");
        uStarDistanceLoc = starShader.uniformLocation("uStarDistance");

        initBodies();

        initialized = true;
    }

    private void initBodies() {
        bodyVao = glGenVertexArrays();
        glBindVertexArray(bodyVao);

        bodyVbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, bodyVbo);

        // Allocate initial buffer (we'll resize if needed). Dynamic because updated every frame.
        glBufferData(GL_ARRAY_BUFFER, (long) 1024 * 8 * Float.BYTES, GL_DYNAMIC_DRAW);

        // layout(location=0): vec4 aRelPosRad
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 4, GL_FLOAT, false, 8 * Float.BYTES, 0L);

        // layout(location=1): vec4 aColorEmissive
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 4, GL_FLOAT, false, 8 * Float.BYTES, 4L * Float.BYTES);

        glBindVertexArray(0);

        bodyShader = new ShaderProgram(BODY_VS, BODY_FS);
        uBodyProjLoc = bodyShader.uniformLocation("uProj");
        uBodyViewRotLoc = bodyShader.uniformLocation("uViewRot");
        uBodyViewportHLoc = bodyShader.uniformLocation("uViewportH");
        uBodySunViewPosLoc = bodyShader.uniformLocation("uSunViewPos");
    }

    private void renderBodies(SimulationScene scene) {
        List<RenderBody> bodies = scene.bodies();
        if (bodies.isEmpty()) return;

        // Ensure CPU buffer is large enough
        int floatsNeeded = bodies.size() * 8;
        if (bodyInstanceBuffer.capacity() < floatsNeeded) {
            bodyInstanceBuffer = BufferUtils.createFloatBuffer((int) (floatsNeeded * 1.5));
        }
        bodyInstanceBuffer.clear();

        // Camera position (float is fine here; we subtract on CPU)
        double camX = scene.camX();
        double camY = scene.camY();
        double camZ = scene.camZ();

        // Pack per-body instance data with camera-relative coords (float)
        for (RenderBody b : bodies) {
            float rx = (float) (b.x - camX);
            float ry = (float) (b.y - camY);
            float rz = (float) (b.z - camZ);

            bodyInstanceBuffer
                    .put(rx).put(ry).put(rz).put(b.radius)
                    .put(b.r).put(b.g).put(b.b).put(b.emissive ? 1.0f : 0.0f);
        }
        bodyInstanceBuffer.flip();

        // Upload to GPU (orphan + subdata avoids stalls)
        glBindBuffer(GL_ARRAY_BUFFER, bodyVbo);
        glBufferData(GL_ARRAY_BUFFER, (long) floatsNeeded * Float.BYTES, GL_DYNAMIC_DRAW);
        glBufferSubData(GL_ARRAY_BUFFER, 0, bodyInstanceBuffer);

        bodyShader.use();

        // Upload uniforms
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);

            scene.projection().get(fb);
            glUniformMatrix4fv(uBodyProjLoc, false, fb);

            fb.clear();
            scene.viewRotationOnly().get(fb);
            glUniformMatrix4fv(uBodyViewRotLoc, false, fb);
        }

        glUniform1f(uBodyViewportHLoc, (float) scene.viewportH());

        // Sun is at world origin. In view-space, its position is just the rotated (-cameraPos).
        // But since we already subtracted cameraPos in the body rel coords, the Sun rel coords would be (-camPos).
        // We compute sun view pos in CPU: viewRot * (-camPos).
        // We'll do it in a simple way: use the camera rotation matrix already uploaded; compute on CPU:
        // However, to keep this minimal, approximate sun view pos as just (0,0,0) if camera is near origin? Not good.
        // We'll compute properly using JOML:
        var sunView = new org.joml.Vector3f((float)(-camX), (float)(-camY), (float)(-camZ));
        scene.viewRotationOnly().transformPosition(sunView);
        glUniform3f(uBodySunViewPosLoc, sunView.x, sunView.y, sunView.z);

        glEnable(GL_PROGRAM_POINT_SIZE);
        glDisable(GL_BLEND);

        glBindVertexArray(bodyVao);
        glDrawArrays(GL_POINTS, 0, bodies.size());
        glBindVertexArray(0);
    }

    private void renderStarfield(SimulationScene scene) {
        starShader.use();

        // Upload matrices
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);

            scene.projection().get(fb);
            glUniformMatrix4fv(uProjLoc, false, fb);

            fb.clear();
            scene.viewRotationOnly().get(fb);
            glUniformMatrix4fv(uViewRotLoc, false, fb);
        }

        // Put the star sphere far away
        glUniform1f(uStarDistanceLoc, 900_000f);

        // Point rendering
        glEnable(GL_PROGRAM_POINT_SIZE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glBindVertexArray(starVao);
        glDrawArrays(GL_POINTS, 0, scene.starfield().count);
        glBindVertexArray(0);

        glDisable(GL_BLEND);
    }

    private static final String BODY_VS = """
    #version 330 core
    layout(location = 0) in vec4 aRelPosRad;      // xyz (camera-relative), w radius (world units)
    layout(location = 1) in vec4 aColorEmissive;  // rgb, a emissive flag

    uniform mat4 uProj;
    uniform mat4 uViewRot;
    uniform float uViewportH;
    uniform vec3 uSunViewPos; // Sun position in view space

    out vec3 vColor;
    out float vEmissive;
    out vec3 vViewCenter;
    out float vRadius;
    out vec3 vLightDir;

    void main() {
        // Camera-relative world -> view-space (rotation only)
        vec3 viewCenter = (uViewRot * vec4(aRelPosRad.xyz, 1.0)).xyz;
        vViewCenter = viewCenter;
        vRadius = aRelPosRad.w;

        gl_Position = uProj * vec4(viewCenter, 1.0);

        // Projected diameter in pixels:
        // pointSize = radius * proj[1][1] * viewportH / -z
        float z = -viewCenter.z;
        float sizePx = (vRadius * uProj[1][1] * uViewportH) / max(z, 0.0001);
        gl_PointSize = clamp(sizePx, 2.0, 4096.0);

        vColor = aColorEmissive.rgb;
        vEmissive = aColorEmissive.a;

        // Lighting direction in view space
        vLightDir = normalize(uSunViewPos - viewCenter);
    }
    """;

    private static final String BODY_FS = """
    #version 330 core
    in vec3 vColor;
    in float vEmissive;
    in vec3 vViewCenter;
    in float vRadius;
    in vec3 vLightDir;

    uniform mat4 uProj;

    out vec4 FragColor;

    void main() {
        // Point sprite coord (0..1). Convert to -1..1 circle space.
        vec2 p = gl_PointCoord * 2.0 - 1.0;
        float r2 = dot(p, p);
        if (r2 > 1.0) discard;

        float nz = sqrt(max(0.0, 1.0 - r2));
        vec3 normal = normalize(vec3(p.x, p.y, nz));

        // Simple lighting (match your "balanced realism" later)
        float lambert = max(0.0, dot(normal, normalize(vLightDir)));
        float ambient = 0.18;
        float intensity = mix(ambient, 1.0, lambert);

        vec3 color = vColor * intensity;

        // Emissive bodies (Sun) glow without shading
        if (vEmissive > 0.5) {
            color = vColor * 1.25;
        }

        // --- Correct per-fragment depth for a sphere impostor ---
        // Fragment view position = center + normal * radius
        vec3 fragView = vViewCenter + normal * vRadius;
        vec4 clip = uProj * vec4(fragView, 1.0);
        float ndcZ = clip.z / clip.w;
        gl_FragDepth = ndcZ * 0.5 + 0.5;

        FragColor = vec4(color, 1.0);
    }
    """;


    // Basic point-star shaders. Direction stays at infinity, camera rotation affects it.
    private static final String STAR_VS = """
        #version 330 core
        layout(location = 0) in vec4 aStar; // xyz dir, w brightness

        uniform mat4 uProj;
        uniform mat4 uViewRot;
        uniform float uStarDistance;

        out float vBright;

        void main() {
            vec3 dir = normalize(aStar.xyz);
            vec3 worldPos = dir * uStarDistance;

            // rotation-only view keeps stars from "moving" as camera translates
            vec4 viewPos = uViewRot * vec4(worldPos, 1.0);
            gl_Position = uProj * viewPos;

            vBright = aStar.w;

            // Point size: mostly tiny with occasional brighter ones
            gl_PointSize = mix(1.0, 3.0, vBright);
        }
        """;

    private static final String STAR_FS = """
        #version 330 core
        in float vBright;
        out vec4 FragColor;

        void main() {
            // Soft circular falloff inside point sprite
            vec2 p = gl_PointCoord * 2.0 - 1.0;
            float r2 = dot(p, p);
            if (r2 > 1.0) discard;

            float falloff = exp(-2.0 * r2);
            float a = clamp(vBright * falloff, 0.0, 1.0);

            // white-ish stars
            FragColor = vec4(vec3(1.0), a);
        }
        """;

}
