"""Apply the exposure correction found during visual review."""
import bpy
bpy.context.scene.view_settings.exposure = -1.65
bpy.context.preferences.filepaths.save_version = 0
bpy.ops.wm.save_as_mainfile(filepath=bpy.data.filepath)
