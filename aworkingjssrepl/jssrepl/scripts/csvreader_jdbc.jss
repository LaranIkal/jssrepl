


// Read CSV file using JDB and print content to screen.

const DriverManager = Java.type("java.sql.DriverManager")
const System = Java.type("java.lang.System")

load('config.jss')

var conn = null
try {
  conn = DriverManager.getConnection("jdbc:relique:csv:" + config.CSVDIR)
  print("Connected successfully to: " + config.CSVDIR)

  const stmt = conn.createStatement()
  const rs = stmt.executeQuery("SELECT * FROM data")  // filename without .csv extension

  // Print Header
  const columnNames = []
  for( let i = 1; i <= rs.getMetaData().getColumnCount(); i++ )
    columnNames.push( rs.getMetaData().getColumnName(i) )

  print( columnNames.join("|") )

  // Print Data Columns
  while( rs.next() ) {
    const columnData = []
    for( let i = 1; i <= rs.getMetaData().getColumnCount(); i++ )
      columnData.push( rs.getString(i) )

    print( columnData.join("|") )
  }
  
  rs.close()
  stmt.close()
  conn.close()  
} catch( error ) {
  print( "Error: " + error )
}

//System.exit(0) // This will exit from the REPL

