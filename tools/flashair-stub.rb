#!/usr/bin/env ruby
# frozen_string_literal: true

# A stand-in for a FlashAir card, so that the app can be exercised end to end
# without one (docs/design.md 10, "疑似実機").
#
# It serves a directory tree from disk and speaks the parts of the FlashAir HTTP
# API the app uses: command.cgi ops 100/101/102/104/108/120/140, plain GETs for
# file contents, and thumbnail.cgi.
#
#   bundle install
#   bundle exec ruby tools/flashair-stub.rb --root tools/fixtures/card --port 8080
#
# Deliberate quirks, because the app has to cope with them on a real card:
#
# - responses use CRLF and start with the WLANSD_FILELIST header line
# - dates and times are FAT encoded from the file's mtime
# - Range requests are ignored unless --ranges is passed: resume support is
#   unverified on real cards, and the app must not depend on it
# - op=102 (write status) clears itself once read

require "bundler/setup"

require "base64"
require "optparse"
require "sinatra/base"

class FlashAirStub < Sinatra::Base
  # FAT attribute bits (docs/design.md 2.3).
  ATTRIBUTE_DIRECTORY = 0x10
  ATTRIBUTE_ARCHIVE = 0x20

  SECTOR_SIZE = 512
  TOTAL_SECTORS = 31_088_640

  # A 1x1 pixel JPEG, returned for every thumbnail.cgi request.
  THUMBNAIL_JPEG = Base64.decode64(<<~BASE64)
    /9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0a
    HBwcJC4nICIsIxwcKDcpLDA1NDQ0Hyc5PTgyPDUzNDP/wAALCAABAAEBAREA/8QAFAABAQAAAAAA
    AAAAAAAAAAAAAAr/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/a
    AAwDAQACEQMRAD8AmQA//9k=
  BASE64

  # How often a throttled body is written out, in slices per second.
  THROTTLE_SLICES_PER_SECOND = 10

  configure do
    disable :protection
    # Modular apps log nothing by default, and seeing the requests the app makes
    # is half the point of running the stub.
    enable :logging
    set :card_root, File.expand_path("fixtures/card", __dir__)
    set :ssid, "flashair_STUB"
    set :firmware, "F19BAW3AW2.00.00"
    set :cid, "0123456789ABCDEF0123456789ABCDEF"
    set :ranges, false
    set :throttle, nil
    set :write_status, "1"
  end

  get "/command.cgi" do
    content_type "text/plain"
    answer = case params["op"]
             when "100" then listing(params["DIR"] || "/")
             when "101" then entry_count(params["DIR"] || "/")
             when "102" then take_write_status
             when "104" then settings.ssid
             when "108" then settings.firmware
             when "120" then settings.cid
             when "140" then free_space
             end
    halt 404 if answer.nil?
    answer
  end

  get "/thumbnail.cgi" do
    # The query string is the file path itself, not a name=value pair.
    path = resolve(Rack::Utils.unescape_path(request.query_string))
    halt 404 unless path && File.file?(path) && File.extname(path).downcase.match?(/\.jpe?g\z/)

    content_type "image/jpeg"
    send_body(THUMBNAIL_JPEG)
  end

  get "/*" do
    path = resolve(Rack::Utils.unescape_path(request.path_info))
    halt 404 unless path && File.file?(path)

    content_type "application/octet-stream"
    content = File.binread(path)
    range = settings.ranges ? request.env["HTTP_RANGE"].to_s.match(/bytes=(\d+)-/) : nil
    halt 200, send_body(content) if range.nil?

    offset = range[1].to_i
    status 206
    headers "Content-Range" => "bytes #{offset}-#{content.bytesize - 1}/#{content.bytesize}"
    send_body(content.byteslice(offset..) || "")
  end

  private

  # Resolves a request path to a path inside the card root, refusing to escape it.
  def resolve(path)
    root = settings.card_root
    full = File.expand_path(File.join(root, path.to_s.sub(%r{\A/}, "")))
    full if full == root || full.start_with?("#{root}/")
  end

  def fat_date_time(time)
    [((time.year - 1980) << 9) | (time.month << 5) | time.day,
     (time.hour << 11) | (time.min << 5) | (time.sec / 2)]
  end

  def listing(directory)
    full = resolve(directory)
    return nil unless full && File.directory?(full)

    # For DIR=/ a real card leaves the directory field empty.
    reported = directory == "/" ? "" : directory.chomp("/")
    lines = Dir.children(full).sort.map do |name|
      stat = File.stat(File.join(full, name))
      date, clock = fat_date_time(stat.mtime)
      attribute = stat.directory? ? ATTRIBUTE_DIRECTORY : ATTRIBUTE_ARCHIVE
      "#{reported},#{name},#{stat.directory? ? 0 : stat.size},#{attribute},#{date},#{clock}"
    end
    "#{(["WLANSD_FILELIST"] + lines).join("\r\n")}\r\n"
  end

  def entry_count(directory)
    full = resolve(directory)
    Dir.children(full).size.to_s if full && File.directory?(full)
  end

  def take_write_status
    settings.write_status.tap { settings.set(:write_status, "0") }
  end

  def free_space
    used = Dir.glob(File.join(settings.card_root, "**/*")).sum { |p| File.file?(p) ? File.size(p) : 0 }
    "#{TOTAL_SECTORS - (used / SECTOR_SIZE)}/#{TOTAL_SECTORS},#{SECTOR_SIZE}"
  end

  # A real card is slow. --throttle makes this one slow too, which is what makes
  # cancelling and reconnecting testable at all. Content-Length is set by hand
  # because a streamed body would otherwise be sent chunked, and the card is
  # not that modern.
  def send_body(content)
    return content if settings.throttle.nil?

    headers "Content-Length" => content.bytesize.to_s
    slice = [settings.throttle / THROTTLE_SLICES_PER_SECOND, 1].max
    stream do |out|
      (0...content.bytesize).step(slice) do |offset|
        out << content.byteslice(offset, slice)
        sleep(1.0 / THROTTLE_SLICES_PER_SECOND)
      end
    end
  end
end

options = {
  root: nil,
  port: 8080,
  host: "0.0.0.0",
}

OptionParser.new do |parser|
  parser.on("--root PATH", "directory to serve as the card's storage")
  parser.on("--port PORT", Integer)
  parser.on("--host HOST")
  parser.on("--ranges", "answer Range requests with 206 instead of ignoring them")
  parser.on("--throttle BYTES_PER_SECOND", Integer, "send file bodies at this rate")
  parser.on("--ssid SSID")
  parser.on("--firmware VERSION")
  parser.on("--cid CID")
end.parse!(into: options)

FlashAirStub.set(:card_root, File.expand_path(options[:root])) if options[:root]
%i[ssid firmware cid ranges throttle].each do |name|
  FlashAirStub.set(name, options[name]) if options.key?(name)
end

abort("no such directory: #{FlashAirStub.card_root}") unless File.directory?(FlashAirStub.card_root)

FlashAirStub.run!(port: options[:port], bind: options[:host])
