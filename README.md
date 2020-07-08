# forex-service
---
Hello!
As we have limitations to call one-frame API, we need some cache in the middle between our client's gets and the frame.
We can get all rates by one call and cache them.
Also, it would be nice to have some eager mechanism to pull data from the frame before it expired in order to protect our clients from long calls for fresh rates to frame directly.
But on the other way we may refresh those rates for nobody, so it also nice to have some mechanism that checks activity and switch from eager autorefresh to lazy - that starts to call frame API in case of activity.
The other concern I handle is some retry flow in case we can't get a date from the frame or get an error.
I extend API with bid and ask and create an endpoint to get a list of rates by list of pairs in one-frame's API manner.
#
About structure:
CacheProgram responses for refreshing its underlying cache and switch between modes I've mentioned about.
CacheState is a changeable state that consists of Map to store rates from frame, counter to check activities, and duration for retry.
CacheProgram takes the implementation by which it gets the date from frame - currently it's service to get data from one-frame with its API and model (OneFrameLive).
Program use RatesCacheService service to obtain data from cache but there would be another implementation for example to use one-frame directly. 
There QueueCallsHistoryService I won't mention a lot as I think it's redundant, but the idea was to have some service for storing rates and errors for some configurable period to analyze their counts, exceptions, etc.
#
Cache use next config params:
`expiration-timeout` - that means date wouldn't be relevant in the cache and all gets for the rates would be stack until new cache would be obtained, 5 minutes in our case
`refresh-timeout` - it should be less then expiration-timeout, less enough to process a regular call before expiration happened, to refresh rates from frame under the scene for our clients, for example, 4 minutes.
`wait-timeout` - a timeout we waiting for a call from the frame before considering its as unreachable, should be less then subtraction between expiration and refresh but enough to perform a regular call over the network, for example, 10 seconds. 
#
I'm working on some tests for checking retry mechanism
And there a couple of Todo's where I've been planning to do improvements       
