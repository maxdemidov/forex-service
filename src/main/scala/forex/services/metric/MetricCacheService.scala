package forex.services.metric

class MetricCacheService {

  // todo:
  //  extends api with ask bid values - show them by special query param

  // todo
  //  test - concurrent first call when cache is empty - they should waiting and only one call should processed to frame
  //  test - if cant get rates from frame in time - call often while didn't get and if cache expired process error correctly
}
