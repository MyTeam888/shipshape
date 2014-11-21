#include "KytheGraphRecorder.h"

#include "third_party/kythe/proto/storage.pb.h"

namespace kythe {

static const std::string *const kNodeKindSpellings[] = {
    new std::string("anchor"),   new std::string("file"),
    new std::string("variable"), new std::string("talias"),
    new std::string("tapp"),     new std::string("tnominal"),
    new std::string("record"),   new std::string("sum"),
    new std::string("constant"), new std::string("abs"),
    new std::string("absvar"),   new std::string("name"),
    new std::string("function"), new std::string("callable"),
    new std::string("lookup")};

static const std::string *kEdgeKindSpellings[] = {
    new std::string("/kythe/edge/defines"),
    new std::string("/kythe/edge/named"),
    new std::string("/kythe/edge/is"),
    new std::string("/kythe/edge/ref"),
    new std::string("/kythe/edge/param"),
    new std::string("/kythe/edge/aliases"),
    new std::string("/kythe/edge/ucompletes"),
    new std::string("/kythe/edge/completes"),
    new std::string("/kythe/edge/childof"),
    new std::string("/kythe/edge/specializes"),
    new std::string("/kythe/edge/ref/call"),
    new std::string("/kythe/edge/callableas")};

static const std::string *const kPropertySpellings[] = {
    new std::string("/kythe/loc"),
    new std::string("/kythe/loc/uri"),
    new std::string("/kythe/loc/start"),
    new std::string("/kythe/loc/start/row"),
    new std::string("/kythe/loc/start"),
    new std::string("/kythe/loc/end"),
    new std::string("/kythe/loc/end/row"),
    new std::string("/kythe/loc/end"),
    new std::string("/kythe/text"),
    new std::string("/kythe/complete"),
    new std::string("/kythe/subkind")};

static const std::string *const kKindSpelling =
    new std::string("/kythe/node/kind");

static const std::string *const kEdgePropertySpelling =
    new std::string("/kythe/ordinal");

static const std::string *const kEmptyStringSpelling = new std::string("");

static const std::string *const kRootPropertySpelling = new std::string("/");

const std::string &spelling_of(PropertyID property_id) {
  return *kPropertySpellings[static_cast<ptrdiff_t>(property_id)];
}

const std::string &spelling_of(NodeKindID node_kind_id) {
  return *kNodeKindSpellings[static_cast<ptrdiff_t>(node_kind_id)];
}

const std::string &spelling_of(EdgeKindID edge_kind_id) {
  return *kEdgeKindSpellings[static_cast<ptrdiff_t>(edge_kind_id)];
}

void KytheGraphRecorder::BeginNode(const VName &node_vname,
                                   NodeKindID kind_id) {
  assert(!in_node_);
  node_vname_ = node_vname;
  in_node_ = true;
  kythe::proto::Entry node_fact;
  node_fact.mutable_source()->CopyFrom(node_vname);
  node_fact.set_fact_name(*kKindSpelling);
  node_fact.set_fact_value(
      *kNodeKindSpellings[static_cast<ptrdiff_t>(kind_id)]);
  stream_->Emit(node_fact);
}

void KytheGraphRecorder::AddProperty(PropertyID property_id,
                                     const std::string &property_value) {
  assert(in_node_);
  kythe::proto::Entry node_fact;
  node_fact.mutable_source()->CopyFrom(node_vname_);
  node_fact.set_fact_name(
      *kPropertySpellings[static_cast<ptrdiff_t>(property_id)]);
  node_fact.set_fact_value(property_value);
  stream_->Emit(node_fact);
}

void KytheGraphRecorder::AddProperty(PropertyID property_id,
                                     const size_t property_value) {
  AddProperty(property_id, std::to_string(property_value));
}

void KytheGraphRecorder::EndNode() {
  assert(in_node_);
  in_node_ = false;
}

void KytheGraphRecorder::AddEdge(const VName &edge_from,
                                 EdgeKindID edge_kind_id,
                                 const VName &edge_to) {
  assert(!in_node_);
  kythe::proto::Entry edge_fact;
  edge_fact.mutable_source()->CopyFrom(edge_from);
  edge_fact.set_edge_kind(
      *kEdgeKindSpellings[static_cast<ptrdiff_t>(edge_kind_id)]);
  edge_fact.mutable_target()->CopyFrom(edge_to);
  edge_fact.set_fact_name(*kRootPropertySpelling);
  edge_fact.set_fact_value(*kEmptyStringSpelling);
  stream_->Emit(edge_fact);
}

void KytheGraphRecorder::AddEdge(const VName &edge_from,
                                 EdgeKindID edge_kind_id, const VName &edge_to,
                                 uint32_t ordinal) {
  assert(!in_node_);
  kythe::proto::Entry edge_fact;
  edge_fact.mutable_source()->CopyFrom(edge_from);
  edge_fact.set_edge_kind(
      *kEdgeKindSpellings[static_cast<ptrdiff_t>(edge_kind_id)]);
  edge_fact.mutable_target()->CopyFrom(edge_to);
  edge_fact.set_fact_name(*kEdgePropertySpelling);
  edge_fact.set_fact_value(std::to_string(ordinal));
  stream_->Emit(edge_fact);
}

void KytheGraphRecorder::AddFileContent(const VName &file_vname,
                                        const llvm::StringRef &file_content) {
  BeginNode(file_vname, NodeKindID::kFile);
  AddProperty(PropertyID::kText, file_content.str());
  EndNode();
}

}  // namespace kythe
