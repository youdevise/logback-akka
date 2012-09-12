# Single layout for Logstash formatted log messages
  * LogstashLayout: A layout that generates JSON formatted messages suitable for use by Logstash

We needed a Logback Layout that produced messages in a suitable format. To do so we modified the Redis version from the original project.
This project simply contains the layout and a minimized set of dependencies that are more compatible with the rest of our system. 

This layout parses the log message text as JSON and merges it into the @message. 
Non-JSON text is placed directlty into @text within @message.
