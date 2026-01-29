package psp.desktop.render;

import static org.lwjgl.opengl.GL20.*;

public final class ShaderProgram implements AutoCloseable {
    private final int programId;

    public ShaderProgram(String vertexSrc, String fragmentSrc) {
        int vs = compile(GL_VERTEX_SHADER, vertexSrc);
        int fs = compile(GL_FRAGMENT_SHADER, fragmentSrc);

        programId = glCreateProgram();
        glAttachShader(programId, vs);
        glAttachShader(programId, fs);
        glLinkProgram(programId);

        if (glGetProgrami(programId, GL_LINK_STATUS) == 0) {
            String log = glGetProgramInfoLog(programId);
            glDeleteProgram(programId);
            glDeleteShader(vs);
            glDeleteShader(fs);
            throw new RuntimeException("Shader link failed:\n" + log);
        }

        glDetachShader(programId, vs);
        glDetachShader(programId, fs);
        glDeleteShader(vs);
        glDeleteShader(fs);
    }

    private static int compile(int type, String src) {
        int id = glCreateShader(type);
        glShaderSource(id, src);
        glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) == 0) {
            String log = glGetShaderInfoLog(id);
            glDeleteShader(id);
            throw new RuntimeException("Shader compile failed:\n" + log);
        }
        return id;
    }

    public void use() { glUseProgram(programId); }
    public int id() { return programId; }

    public int uniformLocation(String name) {
        return glGetUniformLocation(programId, name);
    }

    @Override
    public void close() {
        glDeleteProgram(programId);
    }
}
