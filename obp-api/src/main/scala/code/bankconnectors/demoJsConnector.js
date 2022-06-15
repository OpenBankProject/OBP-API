/**
 * This is a js dynamic message doc, just implement the method body,
 * The javascript run in graalvm, it is not a nodejs environment, so Node.js’
 * built-in modules will not be recognized, such as 'fs', 'events', or 'http' or
 * Node.js-specific functions such as setTimeout() or setInterval()
 * @param args
 * @returns {Promise<Object>}
 */
async function processor(args) {
    const [bankId] = args;
    // call java or scala type in this way
    const BigDecimal = Java.type('java.math.BigDecimal');
    // define a class
    class SwiftBic{
        constructor(name, value) {
            this.name = name;
            this.value = value;
        }
    }
    // define async function
    const someAsyncFn = async () => new BigDecimal('123.456')
    // call other async methods
    const data = await someAsyncFn();

    const bank = {
        "bankId":{
            "value":"HelloBank:"+ bankId
        },
        "shortName":"The Royal Bank of Scotland" + data.toString(),
        "fullName":"The Royal Bank of Scotland",
        "logoUrl":"https://www.example.tesobe.com/logo.gif",
        "websiteUrl":"https://www.example.tesobe.com",
        "bankRoutingScheme":"OBP",
        "bankRoutingAddress":"rbs",
        "swiftBic": new SwiftBic("Mock Swift", 10).name,
        "nationalIdentifier":"String",
    }

    return bank;
}