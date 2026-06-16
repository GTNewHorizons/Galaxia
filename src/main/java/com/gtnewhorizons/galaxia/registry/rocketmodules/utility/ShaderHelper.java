package com.gtnewhorizons.galaxia.registry.rocketmodules.utility;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.*;

public class ShaderHelper {

    private static final Logger LOG = LogManager.getLogger(ShaderHelper.class);

    public static int createProgram(String vert, String frag) {
        int program = GL20.glCreateProgram();

        int vertShader = createShader(GL20.GL_VERTEX_SHADER, vert);
        int fragShader = createShader(GL20.GL_FRAGMENT_SHADER, frag);

        GL20.glAttachShader(program, vertShader);
        GL20.glAttachShader(program, fragShader);

        GL20.glLinkProgram(program);

        // maybe deleting and detaching is a good practice?
        GL20.glDetachShader(program, vertShader);
        GL20.glDetachShader(program, fragShader);
        GL20.glDeleteShader(vertShader);
        GL20.glDeleteShader(fragShader);

        GL20.glValidateProgram(program);

        if (GL20.glGetProgrami(program, GL20.GL_VALIDATE_STATUS) == GL11.GL_FALSE) {
            // return 0 to run fallback instead
            return 0;
        }

        return program;
    }

    public static int createProgram(String vert, String geom, String frag) {
        int program = GL20.glCreateProgram();

        int vertShader = createShader(GL20.GL_VERTEX_SHADER, vert);
        int geomShader = createShader(GL32.GL_GEOMETRY_SHADER, geom);
        int fragShader = createShader(GL20.GL_FRAGMENT_SHADER, frag);

        GL20.glAttachShader(program, vertShader);
        GL20.glAttachShader(program, geomShader);
        GL20.glAttachShader(program, fragShader);

        GL20.glLinkProgram(program);

        // maybe good practice?
        GL20.glDetachShader(program, vertShader);
        GL20.glDetachShader(program, geomShader);
        GL20.glDetachShader(program, fragShader);
        GL20.glDeleteShader(vertShader);
        GL20.glDeleteShader(geomShader);
        GL20.glDeleteShader(fragShader);

        GL20.glValidateProgram(program);

        if (GL20.glGetProgrami(program, GL20.GL_VALIDATE_STATUS) == GL11.GL_FALSE) {
            // return 0 to run fallback instead
            return 0;
        }

        return program;
    }

    public static int createComputeProgram(String comp) {
        int program = GL20.glCreateProgram();

        int compShader = createShader(GL43.GL_COMPUTE_SHADER, comp);

        GL20.glAttachShader(program, compShader);

        GL20.glLinkProgram(program);

        // maybe good practice?
        GL20.glDetachShader(program, compShader);
        GL20.glDeleteShader(compShader);

        GL20.glValidateProgram(program);

        if (GL20.glGetProgrami(program, GL20.GL_VALIDATE_STATUS) == GL11.GL_FALSE) {
            String out = GL20.glGetProgramInfoLog(program, 8192);
            System.out.println(out);
            // return 0 to run fallback instead
            return 0;
        }

        return program;
    }

    private static int createShader(int type, String path) {
        // creating shader location
        int shader = GL20.glCreateShader(type);

        // reading the shader file

        String shaderSource = "";

        try (InputStream stream = ShaderHelper.class.getResourceAsStream(path)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

            String line;
            StringBuilder source = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                source.append(line)
                    .append("\n");
            }

            shaderSource = String.valueOf(source);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // applying the shader source string
        GL20.glShaderSource(shader, shaderSource);

        // compiling shader from source
        GL20.glCompileShader(shader);

        return shader;
    }
}
