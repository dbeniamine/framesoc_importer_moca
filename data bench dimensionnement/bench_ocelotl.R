library(ggplot2) 
library(scatterplot3d)

bench <- "result_dim_full.csv"

h <- 4
w <- 8

read_data <- function(file) {
  df <- read.csv(file, header=TRUE, sep = ";", strip.white=TRUE)
  names(df) <- c("TRACE", "NB_EVENTS", "QUERY_TYPE", "NB_THREAD", "NB_EVENT_PER_THREAD", "TS_START", "TS_END", "Filtered ET", "filtered EP", "MICROMODEL_TIME")
  df
}

printV0 <- function(data){
  dtemp<-data
  dtemp<-dtemp[(dtemp$MICROMODEL_TIME > 0),]
  dtemp<-dtemp[(dtemp$NB_EVENTS > 100001),]
  dtemp<-dtemp[(dtemp$NB_EVENTS < 9000000),]
  dtemp$NB_THREAD <- as.factor(dtemp$NB_THREAD)
  xlabel<- "Number of Events per Thread"
  ylabel<- "Execution Time (s)"
  legend<- "Building Time of the Microscopic Model"
  plot<-ggplot(dtemp, aes(x=NB_EVENT_PER_THREAD, y=MICROMODEL_TIME/1000, color=QUERY_TYPE, 
                        shape=NB_THREAD, group=NB_THREAD))
  plot<-plot + geom_point(alpha = 2/3)
  #plot<-plot + geom_line()
  #plot<-plot + theme_bw()
  plot<-plot + labs(x=xlabel,y=ylabel)
  plot<-plot + scale_color_discrete(name="Number of Threads")
  #breaks=cut(dtemp$NB_THREAD, 2)) #c(2, 4, 8, 16),
  #labels=c("2", "4", "8", "16"))
  plot<-plot+ scale_shape_discrete(name="Number of Threads",
  breaks=c(2, 4, 8, 16),
  labels=c("2", "4", "8", "16"))
plot
}

printV1 <- function(data){
  dtemp<-data
  dtemp<-dtemp[(dtemp$MICROMODEL_TIME > 0),]
  dtemp<-dtemp[(dtemp$NB_EVENTS > 1000001),]
  dtemp<-dtemp[(dtemp$NB_EVENTS < 90000000),]
  dtemp$NB_THREAD <- as.factor(dtemp$NB_THREAD)
  xlabel<- "Number of Events per Thread"
  ylabel<- "Execution Time (s)"
  legend<- "Building Time of the Microscopic Model"
  plot<-ggplot(dtemp, aes(x=NB_EVENT_PER_THREAD, y=MICROMODEL_TIME/1000, color=QUERY_TYPE, 
                          shape=NB_THREAD, group=NB_THREAD))
  plot<-plot + geom_point(alpha = 2/3)
  #plot<-plot + geom_line()
  #plot<-plot + theme_bw()
  plot<-plot + labs(x=xlabel,y=ylabel)
  plot<-plot + scale_color_discrete(name="Number of Threads")
  #breaks=cut(dtemp$NB_THREAD, 2)) #c(2, 4, 8, 16),
  #labels=c("2", "4", "8", "16"))
  plot<-plot+ scale_shape_discrete(name="Number of Threads",
                                   breaks=c(2, 4, 8, 16),
                                   labels=c("2", "4", "8", "16"))
  plot
}

printV2 <- function(data){
  dtemp<-data
  dtemp<-dtemp[(dtemp$MICROMODEL_TIME > 0),]
  dtemp<-dtemp[(dtemp$NB_EVENTS > 10000001),]
  dtemp<-dtemp[(dtemp$NB_EVENTS < 900000000),]
  dtemp$NB_THREAD <- as.factor(dtemp$NB_THREAD)
  xlabel<- "Number of Events per Thread"
  ylabel<- "Execution Time (s)"
  legend<- "Building Time of the Microscopic Model"
  plot<-ggplot(dtemp, aes(x=NB_EVENT_PER_THREAD, y=MICROMODEL_TIME/1000, color=QUERY_TYPE, 
                          shape=NB_THREAD, group=NB_THREAD))
  plot<-plot + geom_point(alpha = 2/3)
  #plot<-plot + geom_line()
  #plot<-plot + theme_bw()
  plot<-plot + labs(x=xlabel,y=ylabel)
  plot<-plot + scale_color_discrete(name="Number of Threads")
  #breaks=cut(dtemp$NB_THREAD, 2)) #c(2, 4, 8, 16),
  #labels=c("2", "4", "8", "16"))
  plot<-plot+ scale_shape_discrete(name="Number of Threads",
                                   breaks=c(2, 4, 8, 16),
                                   labels=c("2", "4", "8", "16"))
  plot
}

printV3 <- function(data){
  dtemp<-data
  dtemp<-dtemp[(dtemp$MICROMODEL_TIME > 0),]
  dtemp<-dtemp[(dtemp$NB_EVENTS > 100000001),]
  dtemp<-dtemp[(dtemp$NB_EVENTS < 9000000000),]
  dtemp$NB_THREAD <- as.factor(dtemp$NB_THREAD)
  xlabel<- "Number of Events per Thread"
  ylabel<- "Execution Time (s)"
  legend<- "Building Time of the Microscopic Model for 1000000000"
  title<-"Toto"
  plot<-ggplot(dtemp, aes(x=NB_EVENT_PER_THREAD, y=MICROMODEL_TIME/1000, color=QUERY_TYPE, 
                          shape=NB_THREAD, group=NB_THREAD))
  plot<-plot + geom_point(alpha = 2/3)
  #plot<-plot + geom_line()
  #plot<-plot + theme_bw()
  plot<-plot + labs(x=xlabel,y=ylabel)
  plot<-plot + scale_color_discrete(name="Number of Threads")
  #breaks=cut(dtemp$NB_THREAD, 2)) #c(2, 4, 8, 16),
  #labels=c("2", "4", "8", "16"))
  plot<-plot+ scale_shape_discrete(name="Number of Threads",
                                   breaks=c(2, 4, 8, 16),
                                   labels=c("2", "4", "8", "16"))
  plot
}



printV3inv <- function(data){
  dtemp<-data
  dtemp<-dtemp[(dtemp$MICROMODEL_TIME > 0),]
  dtemp<-dtemp[(dtemp$NB_EVENTS > 100000001),]
  dtemp<-dtemp[(dtemp$NB_EVENTS < 9000000000),]
  dtemp$NB_EVENT_PER_THREAD <- as.factor(dtemp$NB_EVENT_PER_THREAD)
  xlabel<- "Number of Events per Thread"
  ylabel<- "Execution Time (s)"
  legend<- "Building Time of the Microscopic Model"
  plot<-ggplot(dtemp, aes(x=NB_THREAD, y=MICROMODEL_TIME/1000, color=NB_EVENT_PER_THREAD, 
                           group=NB_EVENT_PER_THREAD))
  plot<-plot + geom_point(alpha = 2/3)
  #plot<-plot + geom_line()
  #plot<-plot + theme_bw()
  plot<-plot + labs(x=xlabel,y=ylabel)
  plot<-plot + scale_color_discrete(name="Number of Threads")
  #breaks=cut(dtemp$NB_THREAD, 2)) #c(2, 4, 8, 16),
  #labels=c("2", "4", "8", "16"))
  #plot<-plot+ scale_shape_discrete(name="Number of Threads",
   #                                breaks=c(2, 4, 8, 16),
    #                               labels=c("2", "4", "8", "16"))
  plot
}


args <- commandArgs(trailingOnly = TRUE)
input <- paste(bench, sep="")
data <- read_data(input)
outputCache <- paste("ocelotl_3DnbThread.pdf", sep="")
ggsave(outputCache, plot = print3D(data), width = w, height = h)
outputCache <- paste("ocelotl_evtPerThread.pdf", sep="")
ggsave(outputCache, plot = printToto(data),  width = w, height = h)
outputCache <- paste("ocelotl_evtPerThreadHist.pdf", sep="")
ggsave(outputCache, plot = printHist(data),  width = w, height = h)
outputCache <- paste("ocelotl_dimV0_1000000.pdf", sep="")
ggsave(outputCache, plot = printV0(data),  width = w, height = h)
outputCache <- paste("ocelotl_dimV1_10000000.pdf", sep="")
ggsave(outputCache, plot = printV1(data),  width = w, height = h)
outputCache <- paste("ocelotl_dimV2_100000000.pdf", sep="")
ggsave(outputCache, plot = printV2(data),  width = w, height = h)
outputCache <- paste("ocelotl_dimV3_1000000000.pdf", sep="")
ggsave(outputCache, plot = printV3(data),  width = w, height = h)

outputCache <- paste("ocelotl_dimV0_1000000.png", sep="")
ggsave(outputCache, plot = printV0(data),  width = w, height = h)
outputCache <- paste("ocelotl_dimV1_10000000.png", sep="")
ggsave(outputCache, plot = printV1(data),  width = w, height = h)
outputCache <- paste("ocelotl_dimV2_100000000.png", sep="")
ggsave(outputCache, plot = printV2(data),  width = w, height = h)
outputCache <- paste("ocelotl_dimV3_1000000000.png", sep="")
ggsave(outputCache, plot = printV3(data),  width = w, height = h)

outputCache <- paste("ocelotl_dimV3inv.pdf", sep="")
ggsave(outputCache, plot = printV3inv(data),  width = w, height = h)




