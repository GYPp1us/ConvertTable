import java.nio.file.Files;
import java.nio.file.Path;
import java.io.StringReader;
import com.google.gson.JsonParser;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.client.resources.model.cuboid.UnbakedCuboidGeometry;
import net.minecraft.client.resources.model.cuboid.CuboidFace;
import net.minecraft.client.renderer.FaceInfo;
import com.example.converttable.AnimationTrack;

/** Parse the shipped geometry with Minecraft's actual model parser. */
public final class AssetModelCheck {
	public static void main(String[] args) throws Exception {
		int checked = 0;
		for (String variant : new String[] {"black_gold", "end", "sculk"}) {
			for (String kind : new String[] {"block", "item"}) {
				Path path = Path.of("src/main/resources/assets/convert_table/models", kind, variant + ".json");
				String json = Files.readString(path);
				int expected = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("elements").size();
				var expectedFaces = JsonParser.parseString(Files.readString(Path.of("assets/runtime", variant + "_" + kind + "_faces.json"))).getAsJsonArray();
				try (var reader = new StringReader(json)) {
					var model = CuboidModel.fromStream(reader);
					if (!(model.geometry() instanceof UnbakedCuboidGeometry geometry)
							|| geometry.elements().size() != expected || expected == 0) {
						throw new AssertionError("Missing or discarded geometry: " + path);
					}
					checked++;
					for (int e = 0; e < expected; e++) {
						var element = geometry.elements().get(e);
						var wanted = expectedFaces.get(e).getAsJsonObject();
						var vertices = wanted.getAsJsonArray("vertices");
						var uv = wanted.getAsJsonArray("uv");
						for (var entry : element.faces().entrySet()) {
							var face = entry.getValue();
							for (int corner = 0; corner < 4; corner++) {
								var p = FaceInfo.fromFacing(entry.getKey()).getVertexInfo(corner).select(element.from(), element.to());
								int match = -1;
								for (int j = 0; j < 4; j++) {
									var v = vertices.get(j).getAsJsonArray();
									if (p.distance(v.get(0).getAsFloat(), v.get(1).getAsFloat(), v.get(2).getAsFloat()) < .0001) match = j;
								}
								if (match < 0) throw new AssertionError("Changed geometry: " + path + " face " + e);
								var expectedUV = uv.get(match).getAsJsonArray();
								if (Math.abs(CuboidFace.getU(face.uvs(), face.rotation(), corner) * 16 - expectedUV.get(0).getAsFloat()) > .0001
										|| Math.abs(CuboidFace.getV(face.uvs(), face.rotation(), corner) * 16 - expectedUV.get(1).getAsFloat()) > .0001)
									throw new AssertionError("Rotated or mirrored UV: " + path + " face " + e);
							}
						}
					}
					System.out.println("Parsed Minecraft model: " + path);
				}
			}
		}
		for (String variant : new String[] {"crystal_table", "catalyst_pedestal"}) {
			for (String kind : new String[] {"block", "item"}) {
				Path path = Path.of("src/main/resources/assets/convert_table/models", kind, variant + ".json");
				String json = Files.readString(path);
				try (var reader = new StringReader(json)) {
					var model = CuboidModel.fromStream(reader);
					if (kind.equals("block")) {
						int expected = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("elements").size();
						if (!(model.geometry() instanceof UnbakedCuboidGeometry geometry)
								|| geometry.elements().size() != expected || expected == 0)
							throw new AssertionError("Missing or discarded geometry: " + path);
					}
					checked++;
					System.out.println("Parsed Minecraft model: " + path);
				}
			}
		}
		System.out.println("Validated " + checked + " models using the Minecraft 26.3 parser.");
		var rotation = new AnimationTrack(true, 12, new float[][]{{0, 0}, {12, 360}});
		if (rotation.sample(3) != 90 || rotation.sample(12) != 0 || rotation.sample(120003) != 90)
			throw new AssertionError("Rotation wrap or long-running precision");
		var bob = new AnimationTrack(false, 4, new float[][]{{0, 0}, {2, -1}, {4, 0}});
		for (int t = 0; t <= 800; t++) {
			float value = bob.sample(t / 100.0);
			if (value < -1 || value > 0) throw new AssertionError("Crystal crossed its air gap");
		}
		if (bob.sample(1) != -.5F || bob.sample(2) != -1 || bob.sample(4) != 0)
			throw new AssertionError("Crystal bob endpoints");
		System.out.println("Validated UV orientation and animation bounds/wrapping.");
	}
}
