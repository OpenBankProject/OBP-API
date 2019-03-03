pipeline {
    agent any 
    stages {
        stage('build') {
            steps {
                echo 'Building with maven'
                sh 'mvn --version'
                sh 'mvn -e compile'
            }
        }
        stage('test') {
            steps {
                echo 'Testing'
                sh 'mvn -e verify'
            }
        }
    }
}
