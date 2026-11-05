// GraalVM JavaScript: Read CSV file line by line using Java classes

// Run the script from scripts directory: 
// Linux:../jss csv-to-json.jss

// Use Java classes to read lines

load('config.jss')

var Paths = Java.type('java.nio.file.Paths')
var BufferedReader = Java.type("java.io.BufferedReader")
var FileReader = Java.type("java.io.FileReader")

function CSV_to_JSON() {
  // Path to your CSV file
  //var csvFilePath = config.CSVDIR + "/sample.csv"
  var csvFilePath = Paths.get(config.CSVDIR + "/sample.csv").toAbsolutePath().toString()
  var result;
  var reader;

  try {
      reader = new BufferedReader(new FileReader(csvFilePath))
  } catch (e) {
      result = JSON.stringify({ error: "Cannot read CSV file", message: String(e) })
      throw result

  }

  var headers = null;
  var data = [];
  var line;

  while ((line = reader.readLine()) !== null) {
      line = line.trim();
      if (line === "") continue;
      if (!headers) {
          headers = line.split(",");
      } else {
          var values = line.split(",");
          var obj = {};
          for (var i = 0; i < headers.length; i++) {
              obj[headers[i].trim()] = (values[i] || "").trim();
          }
          data.push(obj);
      }
  }
  reader.close();

  // Wrap array in an object for JSON output
  print(JSON.stringify({ data: data }, null, 2))
}

print("Call The Function, type:CSV_to_JSON()")