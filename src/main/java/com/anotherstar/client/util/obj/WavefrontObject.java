package com.anotherstar.client.util.obj;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lwjgl.opengl.GL11;

import com.anotherstar.common.LoliPickaxe;
import com.google.common.collect.Maps;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class WavefrontObject {

	private static Pattern vertexPattern = Pattern.compile("(v( (\\-){0,1}\\d+(\\.\\d+)?){3,4} *\\n)|(v( (\\-){0,1}\\d+(\\.\\d+)?){3,4} *$)");
	private static Pattern vertexNormalPattern = Pattern.compile("(vn( (\\-){0,1}\\d+(\\.\\d+)?){3,4} *\\n)|(vn( (\\-){0,1}\\d+(\\.\\d+)?){3,4} *$)");
	private static Pattern textureCoordinatePattern = Pattern.compile("(vt( (\\-){0,1}\\d+\\.\\d+){2,3} *\\n)|(vt( (\\-){0,1}\\d+(\\.\\d+)?){2,3} *$)");
	private static Pattern face_V_VT_VN_Pattern = Pattern.compile("(f( \\d+/\\d+/\\d+){3,} *\\n)|(f( \\d+/\\d+/\\d+){3,} *$)");
	private static Pattern face_V_VT_Pattern = Pattern.compile("(f( \\d+/\\d+){3,} *\\n)|(f( \\d+/\\d+){3,} *$)");
	private static Pattern face_V_VN_Pattern = Pattern.compile("(f( \\d+//\\d+){3,} *\\n)|(f( \\d+//\\d+){3,} *$)");
	private static Pattern face_V_Pattern = Pattern.compile("(f( \\d+){3,} *\\n)|(f( \\d+){3,} *$)");
	private static Pattern groupObjectPattern = Pattern.compile("([go]( [\\w\\d\\.]+) *\\n)|([go]( [\\w\\d\\.]+) *$)");

	private static Matcher vertexMatcher, vertexNormalMatcher, textureCoordinateMatcher;
	private static Matcher face_V_VT_VN_Matcher, face_V_VT_Matcher, face_V_VN_Matcher, face_V_Matcher;
	private static Matcher groupObjectMatcher;

	public ArrayList<Vertex> vertices = new ArrayList<Vertex>();
	public ArrayList<Vertex> vertexNormals = new ArrayList<Vertex>();
	public ArrayList<TextureCoordinate> textureCoordinates = new ArrayList<TextureCoordinate>();
	public ArrayList<GroupObject> groupObjects = new ArrayList<GroupObject>();
	private GroupObject currentGroupObject;
	private Map<String, ResourceLocation> mtl = Maps.newHashMap();
	private String fileName;
	private String pathName;

	public WavefrontObject(ResourceLocationRaw resource) throws ModelFormatException {
		this.fileName = resource.toString();
		int index = this.fileName.lastIndexOf('/');
		if (index != -1) {
			this.pathName = this.fileName.substring(0, index + 1);
			index = this.pathName.indexOf(':');
			if (index != -1) {
				this.pathName = this.pathName.substring(index + 1);
			}
		}
		try {
			IResource res = Minecraft.getMinecraft().getResourceManager().getResource(resource);
			loadObjModel(res.getInputStream());
		} catch (IOException e) {
			throw new ModelFormatException("IO Exception reading model format", e);
		}
	}

	public WavefrontObject(String filename, InputStream inputStream) throws ModelFormatException {
		this.fileName = filename;
		loadObjModel(inputStream);
	}

	private void loadObjModel(InputStream inputStream) throws ModelFormatException {
		BufferedReader reader = null;
		String currentLine = null;
		String currentMtl = null;
		int lineCount = 0;
		Vertex vertex;
		TextureCoordinate textureCoordinate;
		Face face;
		GroupObject group;
		try {
			reader = new BufferedReader(new InputStreamReader(inputStream));
			while ((currentLine = reader.readLine()) != null) {
				lineCount++;
				currentLine = currentLine.replaceAll("\\s+", " ").trim();
				if (currentLine.startsWith("#") || currentLine.length() == 0) {
					continue;
				} else if (currentLine.startsWith("mtllib ")) {
					parseMtl(currentLine, lineCount);
				} else if (currentLine.startsWith("usemtl ")) {
					currentMtl = currentLine.substring(7);
				} else if (currentLine.startsWith("v ")) {
					vertex = parseVertex(currentLine, lineCount);
					if (vertex != null) {
						vertices.add(vertex);
					}
				} else if (currentLine.startsWith("vn ")) {
					vertex = parseVertexNormal(currentLine, lineCount);
					if (vertex != null) {
						vertexNormals.add(vertex);
					}
				} else if (currentLine.startsWith("vt ")) {
					textureCoordinate = parseTextureCoordinate(currentLine, lineCount);
					if (textureCoordinate != null) {
						textureCoordinates.add(textureCoordinate);
					}
				} else if (currentLine.startsWith("f ")) {
					if (currentGroupObject == null) {
						currentGroupObject = new GroupObject("Default");
					}
					face = parseFace(currentLine, lineCount);
					if (face != null) {
						face.usemtl = currentMtl;
						currentGroupObject.faces.add(face);
					}
				} else if (currentLine.startsWith("g ") | currentLine.startsWith("o ")) {
					group = parseGroupObject(currentLine, lineCount);
					if (group != null) {
						if (currentGroupObject != null) {
							groupObjects.add(currentGroupObject);
						}
					}
					currentGroupObject = group;
				}
			}
			groupObjects.add(currentGroupObject);
		} catch (IOException e) {
			throw new ModelFormatException("IO Exception reading model format", e);
		} finally {
			try {
				reader.close();
			} catch (IOException e) {
			}
			try {
				inputStream.close();
			} catch (IOException e) {
			}
		}
	}

	@SideOnly(Side.CLIENT)
	public void renderAll(RenderManager manager) {
		Tessellator tessellator = Tessellator.getInstance();
		/*
		 * int mode = tessellator.getBuffer().getDrawMode(); VertexFormat format =
		 * tessellator.getBuffer().getVertexFormat(); tessellator.draw();
		 */
		tessellateAll(manager, tessellator);
		// tessellator.getBuffer().begin(mode, format);
	}

	@SideOnly(Side.CLIENT)
	public void tessellateAll(RenderManager manager, Tessellator tessellator) {
		for (GroupObject groupObject : groupObjects) {
			if (mtl.containsKey(groupObject.name)) {
				manager.renderEngine.bindTexture(mtl.get(groupObject.name));
			}
			groupObject.render(manager, tessellator, mtl);
		}
	}

	@SideOnly(Side.CLIENT)
	public void renderOnly(String... groupNames) {
		for (GroupObject groupObject : groupObjects) {
			for (String groupName : groupNames) {
				if (groupName.equalsIgnoreCase(groupObject.name)) {
					groupObject.render();
				}
			}
		}
	}

	@SideOnly(Side.CLIENT)
	public void tessellateOnly(Tessellator tessellator, String... groupNames) {
		for (GroupObject groupObject : groupObjects) {
			for (String groupName : groupNames) {
				if (groupName.equalsIgnoreCase(groupObject.name)) {
					groupObject.render(tessellator);
				}
			}
		}
	}

	@SideOnly(Side.CLIENT)
	public void renderPart(String partName) {
		for (GroupObject groupObject : groupObjects) {
			if (partName.equalsIgnoreCase(groupObject.name)) {
				groupObject.render();
			}
		}
	}

	@SideOnly(Side.CLIENT)
	public void tessellatePart(Tessellator tessellator, String partName) {
		for (GroupObject groupObject : groupObjects) {
			if (partName.equalsIgnoreCase(groupObject.name)) {
				groupObject.render(tessellator);
			}
		}
	}

	@SideOnly(Side.CLIENT)
	public void renderAllExcept(String... excludedGroupNames) {
		for (GroupObject groupObject : groupObjects) {
			boolean skipPart = false;
			for (String excludedGroupName : excludedGroupNames) {
				if (excludedGroupName.equalsIgnoreCase(groupObject.name)) {
					skipPart = true;
				}
			}
			if (!skipPart) {
				groupObject.render();
			}
		}
	}

	@SideOnly(Side.CLIENT)
	public void tessellateAllExcept(Tessellator tessellator, String... excludedGroupNames) {
		boolean exclude;
		for (GroupObject groupObject : groupObjects) {
			exclude = false;
			for (String excludedGroupName : excludedGroupNames) {
				if (excludedGroupName.equalsIgnoreCase(groupObject.name)) {
					exclude = true;
				}
			}
			if (!exclude) {
				groupObject.render(tessellator);
			}
		}
	}

	private void parseMtl(String line, int lineCount) throws ModelFormatException {
		if (mtl.isEmpty()) {
			line = line.substring(7);
			try {
				IResource res = Minecraft.getMinecraft().getResourceManager().getResource(new ResourceLocationRaw(LoliPickaxe.MODID, pathName == null ? line : pathName + line));
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(res.getInputStream()))) {
					String currentLine = null;
					String currentGroup = null;
					while ((currentLine = reader.readLine()) != null) {
						currentLine = currentLine.replaceAll("\\s+", " ").trim();
						if (currentLine.startsWith("#") || currentLine.length() == 0) {
							continue;
						} else if (currentLine.startsWith("newmtl ")) {
							if (currentGroup == null) {
								currentGroup = currentLine.substring(7);
							} else {
								throw new ModelFormatException("File Format Error: " + currentLine);
							}
						} else if (currentLine.startsWith("map_Kd ")) {
							if (currentGroup != null) {
								mtl.put(currentGroup, new ResourceLocation(LoliPickaxe.MODID, pathName == null ? currentLine.substring(7) : pathName + currentLine.substring(7)));
								currentGroup = null;
							} else {
								throw new ModelFormatException("File Format Error: " + currentLine);
							}
						}
					}
				}
			} catch (IOException e) {
				throw new ModelFormatException("File Not Found: " + line);
			}
		} else {
			throw new ModelFormatException("Double Mtl File: " + line);
		}
	}

	private Vertex parseVertex(String line, int lineCount) throws ModelFormatException {
		Vertex vertex = null;
		if (isValidVertexLine(line)) {
			line = line.substring(line.indexOf(" ") + 1);
			String[] tokens = line.split(" ");
			try {
				if (tokens.length == 2) {
					return new Vertex(Float.parseFloat(tokens[0]), Float.parseFloat(tokens[1]));
				} else if (tokens.length == 3) {
					return new Vertex(Float.parseFloat(tokens[0]), Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]));
				}
			} catch (NumberFormatException e) {
				throw new ModelFormatException(String.format("Number formatting error at line %d", lineCount), e);
			}
		} else {
			throw new ModelFormatException("Error parsing entry ('" + line + "'" + ", line " + lineCount + ") in file '" + fileName + "' - Incorrect format");
		}
		return vertex;
	}

	private Vertex parseVertexNormal(String line, int lineCount) throws ModelFormatException {
		Vertex vertexNormal = null;
		if (isValidVertexNormalLine(line)) {
			line = line.substring(line.indexOf(" ") + 1);
			String[] tokens = line.split(" ");
			try {
				if (tokens.length == 3)
					return new Vertex(Float.parseFloat(tokens[0]), Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]));
			} catch (NumberFormatException e) {
				throw new ModelFormatException(String.format("Number formatting error at line %d", lineCount), e);
			}
		} else {
			throw new ModelFormatException("Error parsing entry ('" + line + "'" + ", line " + lineCount + ") in file '" + fileName + "' - Incorrect format");
		}
		return vertexNormal;
	}

	private TextureCoordinate parseTextureCoordinate(String line, int lineCount) throws ModelFormatException {
		TextureCoordinate textureCoordinate = null;
		if (isValidTextureCoordinateLine(line)) {
			line = line.substring(line.indexOf(" ") + 1);
			String[] tokens = line.split(" ");
			try {
				if (tokens.length == 2)
					return new TextureCoordinate(Float.parseFloat(tokens[0]), 1 - Float.parseFloat(tokens[1]));
				else if (tokens.length == 3)
					return new TextureCoordinate(Float.parseFloat(tokens[0]), 1 - Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]));
			} catch (NumberFormatException e) {
				throw new ModelFormatException(String.format("Number formatting error at line %d", lineCount), e);
			}
		} else {
			throw new ModelFormatException("Error parsing entry ('" + line + "'" + ", line " + lineCount + ") in file '" + fileName + "' - Incorrect format");
		}
		return textureCoordinate;
	}

	private Face parseFace(String line, int lineCount) throws ModelFormatException {
		Face face = null;
		if (isValidFaceLine(line)) {
			face = new Face();
			String trimmedLine = line.substring(line.indexOf(" ") + 1);
			String[] tokens = trimmedLine.split(" ");
			String[] subTokens = null;
			if (tokens.length == 3) {
				face.glDrawingMode = GL11.GL_TRIANGLES;
			} else if (tokens.length == 4) {
				face.glDrawingMode = GL11.GL_QUADS;
			} else {
				face.glDrawingMode = GL11.GL_POLYGON;
			}
			if (isValidFace_V_VT_VN_Line(line)) {
				face.vertices = new Vertex[tokens.length];
				face.textureCoordinates = new TextureCoordinate[tokens.length];
				face.vertexNormals = new Vertex[tokens.length];
				for (int i = 0; i < tokens.length; ++i) {
					subTokens = tokens[i].split("/");
					face.vertices[i] = vertices.get(Integer.parseInt(subTokens[0]) - 1);
					face.textureCoordinates[i] = textureCoordinates.get(Integer.parseInt(subTokens[1]) - 1);
					face.vertexNormals[i] = vertexNormals.get(Integer.parseInt(subTokens[2]) - 1);
				}
				face.faceNormal = face.calculateFaceNormal();
			} else if (isValidFace_V_VT_Line(line)) {
				face.vertices = new Vertex[tokens.length];
				face.textureCoordinates = new TextureCoordinate[tokens.length];
				for (int i = 0; i < tokens.length; ++i) {
					subTokens = tokens[i].split("/");
					face.vertices[i] = vertices.get(Integer.parseInt(subTokens[0]) - 1);
					face.textureCoordinates[i] = textureCoordinates.get(Integer.parseInt(subTokens[1]) - 1);
				}
				face.faceNormal = face.calculateFaceNormal();
			} else if (isValidFace_V_VN_Line(line)) {
				face.vertices = new Vertex[tokens.length];
				face.vertexNormals = new Vertex[tokens.length];
				for (int i = 0; i < tokens.length; ++i) {
					subTokens = tokens[i].split("//");
					face.vertices[i] = vertices.get(Integer.parseInt(subTokens[0]) - 1);
					face.vertexNormals[i] = vertexNormals.get(Integer.parseInt(subTokens[1]) - 1);
				}
				face.faceNormal = face.calculateFaceNormal();
			} else if (isValidFace_V_Line(line)) {
				face.vertices = new Vertex[tokens.length];
				for (int i = 0; i < tokens.length; ++i) {
					face.vertices[i] = vertices.get(Integer.parseInt(tokens[i]) - 1);
				}
				face.faceNormal = face.calculateFaceNormal();
			} else {
				throw new ModelFormatException("Error parsing entry ('" + line + "'" + ", line " + lineCount + ") in file '" + fileName + "' - Incorrect format");
			}
		} else {
			throw new ModelFormatException("Error parsing entry ('" + line + "'" + ", line " + lineCount + ") in file '" + fileName + "' - Incorrect format");
		}
		return face;
	}

	private GroupObject parseGroupObject(String line, int lineCount) throws ModelFormatException {
		GroupObject group = null;
		if (isValidGroupObjectLine(line)) {
			String trimmedLine = line.substring(line.indexOf(" ") + 1);
			if (trimmedLine.length() > 0) {
				group = new GroupObject(trimmedLine);
			}
		} else {
			throw new ModelFormatException("Error parsing entry ('" + line + "'" + ", line " + lineCount + ") in file '" + fileName + "' - Incorrect format");
		}
		return group;
	}

	private static boolean isValidVertexLine(String line) {
		if (vertexMatcher != null) {
			vertexMatcher.reset();
		}
		vertexMatcher = vertexPattern.matcher(line);
		return vertexMatcher.matches();
	}

	private static boolean isValidVertexNormalLine(String line) {
		if (vertexNormalMatcher != null) {
			vertexNormalMatcher.reset();
		}
		vertexNormalMatcher = vertexNormalPattern.matcher(line);
		return vertexNormalMatcher.matches();
	}

	private static boolean isValidTextureCoordinateLine(String line) {
		if (textureCoordinateMatcher != null) {
			textureCoordinateMatcher.reset();
		}
		textureCoordinateMatcher = textureCoordinatePattern.matcher(line);
		return textureCoordinateMatcher.matches();
	}

	private static boolean isValidFace_V_VT_VN_Line(String line) {
		if (face_V_VT_VN_Matcher != null) {
			face_V_VT_VN_Matcher.reset();
		}
		face_V_VT_VN_Matcher = face_V_VT_VN_Pattern.matcher(line);
		return face_V_VT_VN_Matcher.matches();
	}

	private static boolean isValidFace_V_VT_Line(String line) {
		if (face_V_VT_Matcher != null) {
			face_V_VT_Matcher.reset();
		}
		face_V_VT_Matcher = face_V_VT_Pattern.matcher(line);
		return face_V_VT_Matcher.matches();
	}

	private static boolean isValidFace_V_VN_Line(String line) {
		if (face_V_VN_Matcher != null) {
			face_V_VN_Matcher.reset();
		}
		face_V_VN_Matcher = face_V_VN_Pattern.matcher(line);
		return face_V_VN_Matcher.matches();
	}

	private static boolean isValidFace_V_Line(String line) {
		if (face_V_Matcher != null) {
			face_V_Matcher.reset();
		}
		face_V_Matcher = face_V_Pattern.matcher(line);
		return face_V_Matcher.matches();
	}

	private static boolean isValidFaceLine(String line) {
		return isValidFace_V_VT_VN_Line(line) || isValidFace_V_VT_Line(line) || isValidFace_V_VN_Line(line) || isValidFace_V_Line(line);
	}

	private static boolean isValidGroupObjectLine(String line) {
		if (groupObjectMatcher != null) {
			groupObjectMatcher.reset();
		}
		groupObjectMatcher = groupObjectPattern.matcher(line);
		return groupObjectMatcher.matches();
	}

	public String getType() {
		return "obj";
	}

}