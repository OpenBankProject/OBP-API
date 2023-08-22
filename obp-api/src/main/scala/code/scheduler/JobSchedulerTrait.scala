package code.scheduler

trait JobSchedulerTrait {
  def primaryKey: Long
  def jobId: String
  def name: String
  def apiInstanceId: String
}