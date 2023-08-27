# Rates Cache Service

A service designed to cache and retrieve foreign exchange rates.

Internally the application uses a cache that is guaranteed to expire after five minutes. The cache is automatically refreshed every 4.5 minutes using an external api call.


## Assumptions & Choices
1. **Simplicity Over Optimizations:** The current implementation uses a very simple caching strategy relying on the capabilities of the external api to request multiple currency pairs at the same time. If the number of possible currency pairs grows, this strategy might not be optimal. However, it is simple and easy to implement. As the application grows, we can consider more advanced caching strategies.
2. **Fallbacks:** In cases of errors, I've made the decision to return an empty map as a fallback. This ensures that the application won't crash, but it might return less data than expected. This will be reflected in the outgoing api by returning a 404 not found error. In a real-world scenario maybe having slightly stale data is better than having no data at all. This is a decision that should be made based on the business requirements. 
3. **Tests:** Test cases are currently focused on mocking the HTTP client and ensuring that the cache behaves as expected. As the application grows, more extensive testing will be beneficial.

## Simplifications
- **Logging:** Because of my inexperience with logging frameworks in the scala world, especially with the cats and cats-effects frameworks, logging has not been implemented. In a real-world application, integrating with a comprehensive logging system will be crucial.
- **In-memory Cache:** The current implementation uses an in-memory cache, is not distributed and does not persist. This is not ideal for a production application. A more robust caching strategy relying on a distributed cache such as redis will be beneficial. 
- **Error Responses:** The current system has a simple error response mechanism. In real-world scenarios, a more detailed error structure might be beneficial.

## How to Run the Code

Make sure you have SBT installed. Navigate to the project root directory and run:

```bash
sbt run
```

This will compile and start the application.

## Running Tests
From the project root directory:

```bash
sbt test
```

This will run all the tests in the project.

## Future Improvements
1. **Enhanced Error Handling:** Implement a more robust error handling mechanism, especially for external API calls. Currently I couldn't get timeouts or internal client errors to be handled correctly.
2. **Caching Strategy:** Introduce more advanced caching strategies and possibly a distributed cache to handle larger data sets and multiple instances. https://www.baeldung.com/scala/scalacache looks promising.
