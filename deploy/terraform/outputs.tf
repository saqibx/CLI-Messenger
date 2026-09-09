output "ecr_repository_url" {
  value = aws_ecr_repository.app.repository_url
}

output "alb_url" {
  value = "http://${aws_lb.app.dns_name}"
}

output "chat_cname_target" {
  value = aws_lb.app.dns_name
}

output "acm_validation_record" {
  value = {
    name  = one(aws_acm_certificate.cert.domain_validation_options).resource_record_name
    type  = one(aws_acm_certificate.cert.domain_validation_options).resource_record_type
    value = one(aws_acm_certificate.cert.domain_validation_options).resource_record_value
  }
}

output "site_url" {
  value = "https://${var.domain}"
}

output "msg_config_command" {
  value = "msg config server https://${var.domain}"
}
