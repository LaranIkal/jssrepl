// Use Java classes to read from a REST-API

function GetRESTResponse() {
  var jsURI = Java.type("java.net.URI").create("https://weatherapi-com.p.rapidapi.com/current.json?q=53.1%2C-0.13")
  var restRequest = Java.type("java.net.http.HttpRequest")
              .newBuilder()
              .uri(jsURI)
              .header("X-RapidAPI-Key", "2e4dcf19c6msh76e2d3dabb17533p11e59djsn86e4775b13f2")
              .header("X-RapidAPI-Host", "weatherapi-com.p.rapidapi.com")
              .method("GET", Java.type("java.net.http.HttpRequest").BodyPublishers.noBody())
              .build()
              
  var response = Java.type("java.net.http.HttpClient").newHttpClient()
                  .send(restRequest, Java.type("java.net.http.HttpResponse").BodyHandlers.ofString())
  return(response)
}

var restResponse = GetRESTResponse()
print("Response Status: " + restResponse.statusCode())
print(JSON.stringify(JSON.parse(restResponse.body()), null, 2))
