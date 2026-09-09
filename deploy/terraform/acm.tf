resource "aws_acm_certificate" "cert" {
  domain_name       = var.domain
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_acm_certificate_validation" "cert" {
  count           = var.enable_https ? 1 : 0
  certificate_arn = aws_acm_certificate.cert.arn
}
