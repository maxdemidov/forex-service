package forex.programs.metric

object MetricObject {

  // todo:
  //  extends api with ask bid values - show them by special query param
  //  log count of calls to frame per day - process correct error in case they run out off
  //    process parsing errors - set camel case for date from frame in model
  //  test - concurrent first call when cache is empty - they should waiting and only one call should processed to frame
  //  test - if cant get rates from frame in time - call often while didn't get and if cache expired process error correctly

}
