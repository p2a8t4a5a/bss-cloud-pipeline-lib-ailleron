package tests.job.testjob

pipeline {
    agent none
    stages {
        stage("First") {
            steps {
                echo "first"
            }
        }
        stage("Second") {
            steps {
                echo "second"
            }
        }
    }
}
