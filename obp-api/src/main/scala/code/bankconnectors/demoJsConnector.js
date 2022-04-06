/**
 * This is a js dynamic message doc, just implement the method body,
 * The javascript run in graalvm, it is not a nodejs environment, so Node.js’
 * built-in modules will not be recognized, such as 'fs', 'events', or 'http' or
 * Node.js-specific functions such as setTimeout() or setInterval()
 * @param args
 * @returns {Promise<Object>}
 */
async function processor(args) {
    // call java or scala type in this way
    const BigDecimal = Java.type('java.math.BigDecimal');
    // define a class
    class Person{
        constructor(name, age) {
            this.name = name;
            this.age = age;
        }
    }
    // define async function
    const someAsyncFn = async () => new BigDecimal('123.456')
    // call other async methods
    const data = await someAsyncFn();
    return {bankId: "HelloBank:"+ args[0], result: data.toString(), person: new Person("Shuang", 10)};
}