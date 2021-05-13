print('exporting detection measurement')
import qupath.lib.gui.tools.MeasurementExporter
import qupath.lib.objects.PathCellObject
import qupath.lib.images.servers.bioformats.BioFormatsImageServer
// import qupath.lib.images.servers.bioformats.PyramidGeneratingImageServer
import qupath.imagej.images.servers.ImageJServer
import qupath.lib.images.servers.PyramidGeneratingImageServer
import qupath.lib.images.servers.ImageChannel
import qupath.lib.roi.RectangleROI
import java.util.HashMap
import qupath.lib.objects.PathAnnotationObject
import qupath.lib.display.ImageDisplay

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonElement

//------------Function-------------------
def add_annotation_boxes(int image_w, int image_h, ImagePlane image_plane, int current_x_coord = 0, int current_y_coord = 0, float roi_rate = 0.1) {
  int bound = Math.round(1/roi_rate)
  ArrayList<PathObjects> added_objects = new ArrayList<PathObjects>()
  int roi_w = (int)image_w*roi_rate
  int roi_h = (int)image_h*roi_rate
  for(int index_w = 0; index_w < bound; index_w++) {
  for(int index_h = 0; index_h < bound; index_h++) {
    new_x_coord = index_w*roi_h
    new_y_coord = index_h*roi_w
    def current_roi = ROIs.createRectangleROI(current_y_coord+new_y_coord, current_x_coord+new_x_coord, roi_w, roi_h, image_plane)
    def current_annot = PathObjects.createAnnotationObject(current_roi)
    addObject(current_annot)
    added_objects.add(current_annot)
  }
  // def logger = String.format("Current roi top left is: %d %d ",current_x_coord, current_y_coord)
  // print(logger)
  }
  return added_objects
}
def gen_plugin_command(String channel_name, HashMap params) {
  // String plugin_param = String.format('{"detectionImage": %s,  "requestedPixelSizeMicrons": %f,  "backgroundRadiusMicrons": %f,  "medianRadiusMicrons": %f,  "sigmaMicrons": %f,  "minAreaMicrons": %f,  "maxAreaMicrons": %f,  "threshold": %f,  "watershedPostProcess": %b,  "cellExpansionMicrons": %f,  "includeNuclei": true,  "smoothBoundaries": true,  "makeMeasurements": true,  "thresholdCompartment": "Nucleus: Cy7 mean",  "thresholdPositive1": 100.0,  "thresholdPositive2": 200.0,  "thresholdPositive3": 300.0,  "singleThreshold": true}',channel_name, params.get('requestedPixel'), params.get('bgradius'), params.get('medianRadiusMicrons'), params.get('sigmaMicrons'), params.get('minArea'), params.get('maxArea'),  params.get('intensityThreshold'),  params.get("watershedPostProcess"),  params.get("cellExpansionMicrons"))
  String plugin_param = String.format('{"detectionImage": %s,  "backgroundRadius": 15.0,  "medianRadius": 0.0,  "sigma": 3.0,  "minArea": %f,  "maxArea": %f,  "threshold": %f,  "watershedPostProcess": true,  "cellExpansion": 1.0,  "includeNuclei": true,  "smoothBoundaries": true,  "makeMeasurements": true}', channel_name, params.get('minArea'), params.get('maxArea'),params.get('threshold'))
  return plugin_param
}

def run_detection_for_window(PyramidGeneratingImageServer server_func, HashMap map_channel_index, HashMap target_marker, String marker_name, String scene_number, PathAnnotationObject current_annot,ImageDisplay image_display) {
    def channel_index = map_channel_index.get(marker_name)
    def channel_name = server_func.getChannel(channel_index)
    def path_name = String.format('%s/%s',scene_number, channel_name.getName())
    def path_output = buildFilePath(QPEx.PROJECT_BASE_DIR, path_name)
    mkdirs(path_output)
    image_display.setChannelSelected(image_display.availableChannels()[channel_index], true)
    // all_channels[channel_index].setMinDisplay(map_channel_index.get('minDisplay'))
    // all_channels[channel_index].setMaxDisplay(map_channel_index.get('maxDisplay'))
    getCurrentHierarchy().getSelectionModel().setSelectedObject(current_annot) 
    detections = getDetectionObjects()
    removeObjects(detections, false)
    String plugin_params = gen_plugin_command(channel_name.getName(), target_marker)
    // runPlugin('qupath.imagej.detect.cells.PositiveCellDetection', plugin_params)
    runPlugin('qupath.imagej.detect.cells.WatershedCellDetection',plugin_params)

    detections_result = getDetectionObjects()
    def result = new HashMap()
    result.put('detections_result', detections_result)
    result.put('path', path_output)
    result.put('channel_name', channel_name.getName())
    return result
}
def get_json_from_array(ArrayList<String> arr) {
    Gson g = new GsonBuilder().setPrettyPrinting().create()

    String str = g.toJson(arr)
    return str
}

def export_detector_cell_json(ArrayList<PathCellObject> detections_info, String channel_name, String save_path) {

  ArrayList<String> measurement_values = new ArrayList<String>()
  // print(detections_info)
  for (int c = 0; c < detections_info.size(); c++) {
    // print(detections_info[c].getPolygonPoints())
    String current_str = String.format("Name: %s, ROI: %s, Centroid X: %s, Centroid Y: %s, %s",detections_info[c].getPathClass(), detections_info[c].getNucleusROI().getRoiName(), detections_info[c].getNucleusROI().getCentroidX(), detections_info[c].getNucleusROI().getCentroidY(), detections_info[c].getMeasurementList().toString())
    measurement_values.add(current_str)
  }
  measure_json = get_json_from_array(measurement_values)
  String filename =  String.format('%s/measurement_values_%s.json',save_path,channel_name)
  File json_before = new File(filename)
  json_before.write(measure_json)
}

//---Main function---
// def project = getProject()

def viewer = getCurrentViewer()
def image_data = viewer.getImageData()
def server = image_data.getServer()
print(server)
//----------------clean up all annotation from image (if any), add new annotations image 
// remove list of PathObject and their children in hierachy setting
detections = getDetectionObjects()
existing_annotation = getAnnotationObjectsAsArray()
// print(existing_annotation.getClass())
removeObjects(detections, false)
removeObjects(existing_annotation, false)

def image_display = viewer.getImageDisplay()

def channels = image_display.availableChannels()
print(channels)
//  Should output the line below
// [Original, Normalized OD colors, Red, Green, Blue, Hue, Saturation, 
// RGB mean, Red chromaticity, Green chromaticity, Blue chromaticity]
def channel2index = new HashMap()
for (int c = 0; c < server.nChannels(); c++) {
  def channelName = server.getChannel(c.intValue())
  print(String.format('Channel Name: %s, index: %d',channelName.getName(),c.intValue()))
  channel2index.put(channelName.getName(), new Integer(c.intValue()))
}
// output
// INFO: Channel Name: Red, index: 0
// INFO: Channel Name: Green, index: 1
// INFO: Channel Name: Blue, index: 2


// runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "Blue",  
// "backgroundRadius": 15.0,  "medianRadius": 0.0,  "sigma": 2.0,  "minArea": 20.0,  "maxArea": 250.0,  
// "threshold": 1.0,  "watershedPostProcess": true,  "cellExpansion": 1.0,  "includeNuclei": true,  "smoothBoundaries": true,  "makeMeasurements": true}');

print(channel2index.get('Blue'))
print(channel2index.get('Red'))
def dapi_params = new HashMap()
dapi_params.put("minArea", new Float(20.0))
dapi_params.put("maxArea", new Float(250.0))
dapi_params.put("threshold", new Float(30.0))

print('Add annotations')

// def separator = "\t"
current_scene_number = '0419_Detection_100c'
def base_path = buildFilePath(QPEx.PROJECT_BASE_DIR, current_scene_number)
mkdirs(base_path)
print(base_path)

int count_cell = 101
int z = 0
int t = 0
int image_width = server.getWidth() 
int image_height = server.getHeight() 

def plane = ImagePlane.getPlane(z, t)
int counter = 0
// Add the grid of ROI into image
def annotations =  add_annotation_boxes(image_width, image_height, plane, 0, 0, 0.5)
def threshold = 100


def columnsToInclude = new String[]{"Image", "Centroid X px","Centroid Y px", "Nucleus: Area", "Nucleus: Blue mean", "Cell: Blue mean", "Nucleus: Red mean", "Cell: Red mean"}

def exportType = PathCellObject.class
existing_annotations = getAnnotationObjectsAsArray()
// Choose your *full* output path

while (count_cell > threshold || existing_annotations.size() > 0 ) {
    existing_annotations = getAnnotationObjectsAsArray()
    print('-------------------------------------')
    print(existing_annotations.size())
    print('-------------------------------------')
//    1/0
    counter += 1
    for (annot in existing_annotations) {
    	// print(server)
  
        dapi_detection_resuls = run_detection_for_window(server, channel2index, dapi_params, 'Blue', current_scene_number , annot, image_display)
        print('Numb of nuclei detected :'+ dapi_detection_resuls.get('detections_result').size())
        count_cell = dapi_detection_resuls.get('detections_result').size()
        if(count_cell <= 3) {
          removeObject(annot, false)
          // Do nothing from here
        } 
        else if( (count_cell > 3) &&  (count_cell <= threshold)) {
            print('Record this line for dectecting other marker')
            // remove it from current list to avoid duplicate
            def sub_image_width = annot.getROI().x2 - annot.getROI().x
            def sub_image_height = annot.getROI().y2 - annot.getROI().y
            String file_name = String.format("%s_annot_block_x%.0f_y%.0f_w%.0f_h%.0f", dapi_detection_resuls.get('channel_name'), annot.getROI().getAllPoints()[0].getX(), annot.getROI().getAllPoints()[0].getY(),sub_image_width,sub_image_height)
            export_detector_cell_json(dapi_detection_resuls.get('detections_result'), file_name, dapi_detection_resuls.get('path'))
			String filename =  String.format('%s/%s_measurement_values_x%.0f_y%.0f_w%.0f_h%.0f.tsv',dapi_detection_resuls.get('path'),'Covid19_channel',annot.getROI().getAllPoints()[0].getX(), annot.getROI().getAllPoints()[0].getY(),sub_image_width,sub_image_height)
            // def outputPath = "/Volumes/BiomedML/Projects/QuPath_projects/Covid19/scripts/Demo_measurements.tsv"
			def outputFile = new File(filename)
            saveDetectionMeasurements(filename, columnsToInclude)
            print(outputFile)
            removeObject(annot, false)
        }
        else {
            // spawn new annotation boxes from the current coordinate
            int sub_image_width = annot.getROI().x2 - annot.getROI().x
            int sub_image_height = annot.getROI().y2 - annot.getROI().y
            annotations =  add_annotation_boxes(sub_image_width, sub_image_height, plane, (int)annot.getROI().y, (int)annot.getROI().x, 0.5)
            removeObject(annot, false)
        }
    }
    if (counter >= 7){
        print('too much iteration already')
        break
    }
}


//
// saveDetectionMeasurements(outputPath, columnsToInclude)
print("Done!")


