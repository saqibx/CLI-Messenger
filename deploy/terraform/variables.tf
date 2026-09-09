variable "region" {
  default = "us-east-1"
}

variable "project" {
  default = "devchat"
}

variable "image_tag" {
  default = "latest"
}

variable "container_cpu" {
  default = 512
}

variable "container_memory" {
  default = 1024
}

variable "desired_count" {
  default = 1
}

variable "table_prefix" {
  default = "DevChat"
}

variable "domain" {
  default = "chat.saqibmazhar.com"
}

variable "enable_https" {
  default = false
}
